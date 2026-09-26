package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.disguise.OutpostDisguiseRelations;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostAlertManager;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostStackManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskAttack;
import com.github.tartaricacid.touhoulittlemaid.api.task.IRangedAttackTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.TickEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionBetrayalManager {

    // ===== 配置参数 =====
    private static final int TRUST_THRESHOLD = 10;
    private static final int FEAR_THRESHOLD = 90;
    private static final int DURATION_THRESHOLD = 200;      // 30 秒
    private static final float EXPLOSION_POWER = 6.0f;
    private static final float ATTACK_SPEED = 0.6f;
    private static final int SEARCH_RADIUS = 10;
    private static final int OUTPOST_RETARGET_INTERVAL = 20;
    private static final String OUTPOST_RETURN_NEEDED = "CallResponseOutpostReturnNeeded";
    private static final String OUTPOST_RETURN_POS = "CallResponseOutpostReturnPos";
    private static final String OUTPOST_RETURN_RETRY_AT = "CallResponseOutpostReturnRetryAt";
    private static final String OUTPOST_RETURN_LAST_DISTANCE = "CallResponseOutpostReturnLastDistance";
    private static final String OUTPOST_NO_TARGET_SINCE = "CallResponseOutpostNoTargetSince";
    private static final String OUTPOST_PURSUIT_TARGET = "CallResponseOutpostPursuitTarget";
    private static final String OUTPOST_PURSUIT_LAST_POS = "CallResponseOutpostPursuitLastPos";
    private static final String OUTPOST_PURSUIT_PROGRESS_AT = "CallResponseOutpostPursuitProgressAt";
    private static final String OUTPOST_PURSUIT_HIT_AT = "CallResponseOutpostPursuitHitAt";
    private static final String OUTPOST_BLOCKED_TARGET = "CallResponseOutpostBlockedTarget";
    private static final String OUTPOST_BLOCKED_UNTIL = "CallResponseOutpostBlockedUntil";
    private static final long OUTPOST_TELEPORT_AFTER = 20L * 30L;
    private static final long OUTPOST_STUCK_TICKS = 20L * 5L;
    private static final long OUTPOST_BLOCKED_TICKS = 20L * 5L;
    private static final double OUTPOST_RETARGET_MIN_GAIN_SQR = 4.0D;
    private static final double OUTPOST_RETARGET_DISTANCE_RATIO_SQR = 0.64D;
    private static final int ATTACK_COOLDOWN_TICKS = 10;
    private static final int BETRAYAL_REPLY_COOLDOWN = 100;  // 3 秒冷却
    private static final String BETRAYAL_NBT_TAG = "IsBetraying";

    // ===== 状态存储 =====
    private static final Map<UUID, Integer> dangerTimer = new ConcurrentHashMap<>();
    private static final Map<UUID, SuppressedDanger> suppressedDanger = new ConcurrentHashMap<>();
    /** 只撤销本模组这次归位写入的 WALK_TARGET，不碰随后由找床等行为接管的路径。 */
    private static final Map<EntityMaid, WalkTarget> outpostReturnWalkTargets = new WeakHashMap<>();
    private record SuppressedDanger(UUID owner, long expiresAt) {}

    public static boolean hasActiveDangerTimer(EntityMaid maid) {
        return !isBetraying(maid) && !BetrayalOutpostMaidData.isOutpostMaid(maid)
                && dangerTimer.getOrDefault(maid.getUUID(), 0) > 0;
    }

    public static void resumeDangerAfterIntimidation(EntityMaid maid, UUID ownerId) {
        UUID id = maid.getUUID();
        dangerTimer.put(id, 0);
        suppressedDanger.remove(id);
        if (!maid.isAlive() || !maid.isTame() || !ownerId.equals(maid.getOwnerUUID())
                || isBetraying(maid) || BetrayalOutpostMaidData.isOutpostMaid(maid)) return;
        EmotionData.EmotionValues values = EmotionData.get(maid, ownerId);
        if (values.trust() < TRUST_THRESHOLD && values.fear() > FEAR_THRESHOLD) {
            suppressedDanger.put(id, new SuppressedDanger(ownerId,
                    maid.level().getServer().getTickCount() + DURATION_THRESHOLD + 40L));
        }
    }

    public static void cancelDangerAfterIntimidation(EntityMaid maid) {
        dangerTimer.remove(maid.getUUID());
        suppressedDanger.remove(maid.getUUID());
    }

    /** Preserve outpost return/stuck durations while its AI is intentionally paused. */
    public static void pauseOutpostReturnClock(EntityMaid maid) {
        if (!BetrayalOutpostMaidData.isOutpostMaid(maid)) return;
        CompoundTag data = maid.getPersistentData();
        for (String key : new String[]{OUTPOST_NO_TARGET_SINCE, OUTPOST_PURSUIT_PROGRESS_AT,
                OUTPOST_PURSUIT_HIT_AT, OUTPOST_RETURN_RETRY_AT}) {
            if (data.contains(key, CompoundTag.TAG_LONG)) data.putLong(key, data.getLong(key) + 1L);
        }
    }
    private static final Map<UUID, Boolean> isBetraying = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> attackCooldown = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> attackCountMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastReplyTimeMap = new ConcurrentHashMap<>();
    private static final Map<UUID, VictimFleeState> victimFleeStates = new ConcurrentHashMap<>();
    private static final long VICTIM_FLEE_TICKS = 20L * 20L;
    private static final double VICTIM_SAFE_DISTANCE_SQR = 16.0D * 16.0D;

    private record VictimFleeState(EntityMaid victim, UUID attackerId, long until) {
    }

    // ===== 检查背叛状态（先内存，再从NBT恢复） =====
    public static boolean isBetraying(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        if (isBetraying.containsKey(maidId)) {
            return isBetraying.get(maidId);
        }
        CompoundTag data = maid.getPersistentData();
        if (data.contains(BETRAYAL_NBT_TAG)) {
            boolean state = data.getBoolean(BETRAYAL_NBT_TAG);
            if (state) {
                isBetraying.put(maidId, true);
            }
            return state;
        }
        return false;
    }

    /** 真正由情绪系统触发的背叛；据点复仇女仆只复用战斗执行，不属于这类事件。 */
    public static boolean isActualBetrayal(EntityMaid maid) {
        return isBetraying(maid) && !BetrayalOutpostMaidData.isOutpostMaid(maid);
    }

    private static void ensureUntamed(EntityMaid maid) {
        if (maid.isTame()) maid.setTame(false);
        if (maid.getOwnerUUID() != null) maid.setOwnerUUID(null);
    }

    @SubscribeEvent
    public void onBetrayerJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof EntityMaid maid
                && isActualBetrayal(maid)) ensureUntamed(maid);
    }

    @SubscribeEvent
    public void onBetrayerTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide
                && isActualBetrayal(maid) && (maid.isTame() || maid.getOwnerUUID() != null)) {
            ensureUntamed(maid);
        }
    }

    /** 让据点女仆沿用现有背叛索敌与攻击节奏，但保留生成器分配的真实工作模式。 */
    public static void initializeOutpostBetrayer(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        isBetraying.put(maidId, true);
        maid.getPersistentData().putBoolean(BETRAYAL_NBT_TAG, true);
        dangerTimer.remove(maidId);
        attackCooldown.remove(maidId);
        if (maid.isInSittingPose()) maid.setInSittingPose(false);
        ensureUntamed(maid);
        maid.setTarget(null);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        maid.setAggressive(true);
    }

    // ===== 处理受害者被背叛女仆攻击 =====
    public static void onVictimAttackedByBetrayer(EntityMaid victim, EntityMaid betrayer) {
        if (!isActualBetrayal(betrayer)) return;
        // 只有有主人的女仆才会触发求救（野生女仆不触发）
        if (!victim.isTame() || victim.getOwner() == null) return;
        if (victim.level().isClientSide) return;

        UUID victimId = victim.getUUID();
        long now = victim.level().getGameTime();
        MaidMovementControl.begin(victim, MaidMovementControl.Reason.BETRAYAL_VICTIM_FLEE,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
        victim.setInSittingPose(false);
        victimFleeStates.put(victimId, new VictimFleeState(victim, betrayer.getUUID(), now + VICTIM_FLEE_TICKS));
        updateVictimFleeTarget(victim, betrayer);

        Long lastReply = lastReplyTimeMap.get(victimId);
        if (lastReply != null && now - lastReply < BETRAYAL_REPLY_COOLDOWN) return;

        int count = attackCountMap.getOrDefault(victimId, 0) + 1;
        attackCountMap.put(victimId, count);

        String instruction;
        float healthRatio = victim.getHealth() / victim.getMaxHealth();
        boolean isNearDeath = healthRatio < 0.4;

        String tendencyDesc = EmotionData.getTendencyPromptSuffix(victim, victim.getOwnerUUID());
        if (count == 1) {
            instruction = "你完全没想到同类会攻击你！那个女仆的眼睛里没有理智，只有疯狂。" + tendencyDesc + " 你感到震惊和困惑，请说一段话表达你的惊恐和求救。";
        } else if (count <= 4 && !isNearDeath) {
            instruction = "那个疯狂的女仆还在攻击你，你身上已经添了好几道伤口。" + tendencyDesc + " 你越来越害怕，感觉自己快要支撑不住了。请用断断续续的语气向你的主人求救，声音里要有快要哭出来的绝望。";
        } else {
            instruction = "你快要死了……视野开始模糊，耳边只剩下自己的心跳声。" + tendencyDesc + " 你感觉生命正在流逝，身上没一处不疼的。请用虚弱的、断断续续的语气表达你的绝望，你只能等着主人来救你——或者在最后的时刻想念主人。";
        }

        LivingEntity ownerEntity = victim.getOwner();
        if (ownerEntity instanceof ServerPlayer ownerPlayer) {
            if (ownerPlayer.distanceTo(victim) < 16) {
                Component victimName = victim.getName();
                ownerPlayer.sendSystemMessage(
                        Component.translatable("message.callresponse.emotion.betrayal_warning_prefix")
                                .append(victimName)
                                .append(Component.translatable("message.callresponse.emotion.betrayal_warning_suffix"))
                );
            }
            MaidResponder.processBroadcast(ownerPlayer, Collections.singletonList(victim), instruction, false);
        } else {
            victim.getChatBubbleManager().addTextChatBubble("bubble.callresponse.emotion.help");
        }

        // 逃跑路径由服务器 tick 持续维护，不能坐下，否则 canBrainMoving() 会立刻阻止 WALK_TARGET。
        lastReplyTimeMap.put(victimId, now);
    }

    private static void updateVictimFleeTarget(EntityMaid victim, EntityMaid betrayer) {
        victim.setTarget(null);
        victim.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        BlockPos betrayerPos = betrayer.blockPosition();
        BlockPos victimPos = victim.blockPosition();
        double dx = victimPos.getX() - betrayerPos.getX();
        double dz = victimPos.getZ() - betrayerPos.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 0.1) {
            double targetX = victimPos.getX() + (dx / dist) * 15;
            double targetZ = victimPos.getZ() + (dz / dist) * 15;
            BlockPos fleePos = new BlockPos((int) targetX, victimPos.getY(), (int) targetZ);
            victim.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(fleePos), 1.3f, 1));
        }
    }

    private static void tickVictimFleeStates() {
        for (VictimFleeState state : List.copyOf(victimFleeStates.values())) {
            EntityMaid victim = state.victim();
            LivingEntity attacker = victim.level() instanceof ServerLevel level
                    ? level.getEntity(state.attackerId()) instanceof LivingEntity living ? living : null : null;
            long now = victim.level().getGameTime();
            if (!victim.isAlive() || victim.isRemoved() || attacker == null || !attacker.isAlive()
                    || attacker.level() != victim.level() || now >= state.until()
                    || victim.distanceToSqr(attacker) >= VICTIM_SAFE_DISTANCE_SQR) {
                victimFleeStates.remove(victim.getUUID(), state);
                MaidMovementControl.clearNavigation(victim);
                MaidMovementControl.end(victim, MaidMovementControl.Reason.BETRAYAL_VICTIM_FLEE);
                continue;
            }
            if (attacker instanceof EntityMaid betrayer) {
                updateVictimFleeTarget(victim, betrayer);
            }
        }
    }

    // ===== 定时检查 =====
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        long serverNow = event.getServer().getTickCount();
        suppressedDanger.entrySet().removeIf(entry -> entry.getValue().expiresAt() < serverNow);

        tickVictimFleeStates();

        // 同一只女仆可能同时落入多名玩家的查询范围。必须只按她自己的主人结算一次，
        // 否则多人服中非主人的默认双值会在同一 tick 把主人刚累积的计时清零。
        Set<UUID> processed = new HashSet<>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(32))
                    .forEach(maid -> {
                        UUID maidId = maid.getUUID();
                        if (!processed.add(maidId)) return;
                        if (IntimidationManager.isIntimidated(maid)) return;

                        // 狩猎优先级 > 背叛：狩猎中的女仆不处理背叛
                        if (HuntOrderManager.isHunting(maid)) {
                            return;
                        }

                        // 检查是否已背叛
                        if (isBetraying(maid)) {
                            // 据点女仆由自身实体 tick 结算，不能依赖附近玩家的 32 格扫描归位。
                            if (!BetrayalOutpostMaidData.isOutpostMaid(maid)) performBetrayalActions(maid);
                            return;
                        }

                        // 只有有主人的女仆才能触发新背叛
                        UUID ownerId = maid.getOwnerUUID();
                        if (!maid.isTame() || ownerId == null) return;
                        SuppressedDanger suppressed = suppressedDanger.get(maidId);
                        if (suppressed != null && !suppressed.owner().equals(ownerId)) {
                            suppressedDanger.remove(maidId);
                            suppressed = null;
                        }
                        ServerPlayer owner = event.getServer().getPlayerList().getPlayer(ownerId);
                        if (owner == null || owner.level() != maid.level()
                                || owner.distanceToSqr(maid) > 32.0D * 32.0D) return;

                        EmotionData.EmotionValues values = EmotionData.get(maid, owner);
                        int trust = values.trust();
                        int fear = values.fear();

                        if (trust < TRUST_THRESHOLD && fear > FEAR_THRESHOLD) {
                            int timer = dangerTimer.getOrDefault(maidId, 0) + 1;
                            dangerTimer.put(maidId, timer);

                            if (timer >= DURATION_THRESHOLD) {
                                if (suppressedDanger.remove(maidId) != null) {
                                    trySuppressedDangerDeath(maid);
                                } else triggerBetrayal(maid, owner);
                            }
                        } else {
                            dangerTimer.put(maidId, 0);
                            suppressedDanger.remove(maidId);
                        }
                    });
        }
    }

    public static void tickOutpostCombat(EntityMaid maid) {
        if (maid.level() instanceof ServerLevel && maid.isAlive()
                && !IntimidationManager.isIntimidated(maid)
                && BetrayalOutpostMaidData.isOutpostMaid(maid) && isBetraying(maid)) {
            performBetrayalActions(maid);
        }
    }

    // ===== 触发背叛 =====
    private static void trySuppressedDangerDeath(EntityMaid maid) {
        UUID id = maid.getUUID();
        dangerTimer.remove(id);
        if (!maid.isAlive() || BetrayalOutpostMaidData.isOutpostMaid(maid)) return;
        // Mark only for the synchronous, normal death chain; its existing death listener
        // performs exactly one configured explosion. If protection keeps her alive,
        // remove the marker and never retry this one-shot attempt.
        maid.getPersistentData().putBoolean(BETRAYAL_NBT_TAG, true);
        isBetraying.put(id, true);
        try {
            maid.hurt(maid.damageSources().generic(), maid.getHealth() + maid.getMaxHealth() + 1.0F);
        } catch (RuntimeException ex) {
            CallResponseMod.LOGGER.warn("Suppressed betrayal death safely aborted for maid {}", id, ex);
        } finally {
            if (maid.isAlive()) {
                isBetraying.remove(id);
                maid.getPersistentData().remove(BETRAYAL_NBT_TAG);
            }
        }
    }

    private static void triggerBetrayal(EntityMaid maid, ServerPlayer player) {
        UUID maidId = maid.getUUID();
        if (isBetraying(maid)) return;

        isBetraying.put(maidId, true);
        maid.getPersistentData().putBoolean(BETRAYAL_NBT_TAG, true); // 持久化
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.BETRAYAL,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE,
                        MaidMovementControl.Field.TASK, MaidMovementControl.Field.OWNER));

        dangerTimer.remove(maidId);
        attackCooldown.remove(maidId);

        if (maid.isInSittingPose()) maid.setInSittingPose(false);
        ensureUntamed(maid);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);

        ResourceLocation attackTaskId = ResourceLocation.parse("touhou_little_maid:attack");
        TaskManager.findTask(attackTaskId).ifPresent(maid::setTask);

        maid.setAggressive(true);

        Component maidName = maid.getName();
        if (player != null) {
            player.sendSystemMessage(
                    Component.literal("§c§l")
                            .append(maidName)
                            .append(Component.translatable("message.callresponse.emotion.betrayed"))
            );
        }
        maid.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
    }

    // ===== 执行背叛行为 =====
    private static void performBetrayalActions(EntityMaid maid) {
        if (!maid.isAlive()) return;
        ensureUntamed(maid);
        if (IntimidationManager.isIntimidated(maid)) return;

        UUID maidId = maid.getUUID();

        // 确保内存状态一致（如果内存中不存在但NBT存在，恢复）
        if (!isBetraying.containsKey(maidId) && maid.getPersistentData().contains(BETRAYAL_NBT_TAG)) {
            boolean state = maid.getPersistentData().getBoolean(BETRAYAL_NBT_TAG);
            if (state) {
                isBetraying.put(maidId, true);
            }
        }

        boolean outpost = BetrayalOutpostMaidData.isOutpostMaid(maid);
        // TLM's ATTACK_TARGET memory is authoritative for its movement and weapon behaviors.
        LivingEntity target = outpost
                ? maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(maid.getTarget())
                : maid.getTarget();
        if (target != null && (isInvalidTarget(maid, target)
                || (outpost
                && !BetrayalOutpostAlertManager.canKeepOrDetectTarget(maid, target)))) {
            if (outpost) BetrayalOutpostAlertManager.clearCombat(maid);
            else {
                maid.setTarget(null);
                maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            target = null;
        }

        if (target != null && BetrayalOutpostMaidData.isOutpostMaid(maid)
                && maid.tickCount % OUTPOST_RETARGET_INTERVAL == 0) {
            LivingEntity closer = findClearlyCloserOutpostTarget(maid, target);
            if (closer != null) {
                BetrayalOutpostAlertManager.enterCombat(maid, closer);
                target = closer;
            }
        }

        if (target == null || !target.isAlive()) {
            target = selectTarget(maid);
            if (target != null) {
                if (outpost) BetrayalOutpostAlertManager.enterCombat(maid, target);
                else {
                    maid.setTarget(target);
                    maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
                }
            } else {
                // 营地内正常活动无需集合；仅真正滞留在营地外时启动一次归位。
                if (outpost) {
                    maid.getPersistentData().remove(OUTPOST_PURSUIT_TARGET);
                    updateOutpostReturn(maid);
                } else destroyNearbyContainer(maid);
                return;
            }
        }

        if (outpost) {
            cancelOutpostReturn(maid);
            BetrayalOutpostAlertManager.enterCombat(maid, target);
            if (!maintainOutpostPursuit(maid, target)) {
                updateOutpostReturn(maid);
                return;
            }
        }

        BetrayalOutpostMaidData.tickDialogue(maid, target);

        // The three assault maids use TLM TaskAttack's own movement and melee cooldown while working.
        if (outpost && isTlmMeleeActive(maid)) return;

        int cooldown = attackCooldown.getOrDefault(maidId, 0);
        if (cooldown > 0) {
            attackCooldown.put(maidId, cooldown - 1);
            return;
        }

        if (target instanceof ServerPlayer player) {
            performAttackOnPlayer(maid, player);
        } else if (target instanceof EntityMaid otherMaid) {
            performAttackOnMaid(maid, otherMaid);
        } else if (BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            performAttackOnLiving(maid, target);
        } else {
            maid.setTarget(null);
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    // ===== 选择目标（优先玩家，其次任何女仆，不检查主人） =====
    private static LivingEntity selectTarget(EntityMaid maid) {
        if (BetrayalOutpostMaidData.isOutpostMaid(maid)
                && BetrayalOutpostAlertManager.isRelaxed(maid)) {
            return BetrayalOutpostAlertManager.findRelaxedTarget(maid);
        }

        LivingEntity nearestPlayer = findNearestPlayer(maid);
        if (nearestPlayer != null) return nearestPlayer;

        if (BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            AABB searchArea = BetrayalOutpostMaidData.pursuitSearchArea(maid);
            return maid.level().getEntitiesOfClass(LivingEntity.class,
                            searchArea,
                            living -> BetrayalOutpostAlertManager.isBaseValidTarget(maid, living))
                    .stream().sorted(java.util.Comparator.comparingDouble(maid::distanceToSqr))
                    .limit(6).filter(living -> BetrayalOutpostAlertManager.canKeepOrDetectTarget(maid, living))
                    .findFirst().orElse(null);
        }

        // 攻击任何女仆（包括野生，但野生不会触发求救）
        List<EntityMaid> maids = maid.level().getEntitiesOfClass(EntityMaid.class,
                maid.getBoundingBox().inflate(SEARCH_RADIUS),
                m -> m != maid && m.isAlive());
        if (!maids.isEmpty()) {
            EntityMaid nearestMaid = null;
            double minDist = Double.MAX_VALUE;
            for (EntityMaid m : maids) {
                double dist = maid.distanceToSqr(m);
                if (dist < minDist) {
                    minDist = dist;
                    nearestMaid = m;
                }
            }
            return nearestMaid;
        }

        return null;
    }

    private static LivingEntity findNearestPlayer(EntityMaid maid) {
        List<ServerPlayer> players = maid.level().getEntitiesOfClass(ServerPlayer.class,
                BetrayalOutpostMaidData.isOutpostMaid(maid)
                        ? BetrayalOutpostMaidData.pursuitSearchArea(maid)
                        : maid.getBoundingBox().inflate(SEARCH_RADIUS));
        LivingEntity nearest = null;
        double minDist = Double.MAX_VALUE;
        players.sort(java.util.Comparator.comparingDouble(maid::distanceToSqr));
        int checked = 0;
        for (ServerPlayer p : players) {
            if (BetrayalOutpostMaidData.isOutpostMaid(maid) && checked++ >= 6) break;
            if (p.isAlive() && !p.isSpectator() && !isInvalidTarget(maid, p)
                    && (!BetrayalOutpostMaidData.isOutpostMaid(maid)
                    || (!p.isCreative() && BetrayalOutpostAlertManager.canKeepOrDetectTarget(maid, p)))) {
                double dist = maid.distanceToSqr(p);
                if (dist < minDist) {
                    minDist = dist;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    // ===== 攻击玩家（保底2点伤害） =====
    private static void performAttackOnPlayer(EntityMaid maid, ServerPlayer player) {
        if (BetrayalOutpostMaidData.isOutpostMaid(maid)
                && OutpostDisguiseRelations.isPacifiedTarget(maid, player)) {
            BetrayalOutpostAlertManager.clearCombat(maid);
            return;
        }
        if (!player.isAlive() || (BetrayalOutpostMaidData.isOutpostMaid(maid)
                && (player.isCreative() || player.isSpectator()))) {
            maid.setTarget(null);
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }

        if (isWithinMeleeRange(maid, player)) {
            maid.swing(InteractionHand.MAIN_HAND);

            double baseDamage = maid.getAttribute(Attributes.ATTACK_DAMAGE) != null ?
                    maid.getAttribute(Attributes.ATTACK_DAMAGE).getValue() : 0.0;
            float finalDamage = Math.max(2.0f, (float) baseDamage);

            boolean success = player.hurt(player.damageSources().mobAttack(maid), finalDamage);
            if (!success) {
                player.hurt(player.damageSources().generic(), finalDamage);
            }

            maid.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.0f);
            attackCooldown.put(maid.getUUID(), ATTACK_COOLDOWN_TICKS);

            if (!player.isAlive()) {
                maid.setTarget(null);
                maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        } else if (!BetrayalOutpostStackManager.isMovementDelegated(maid)) {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(player.blockPosition()), ATTACK_SPEED, 1));
        }
    }

    // ===== 攻击其他女仆（触发求救，但只对有主女仆） =====
    private static void performAttackOnMaid(EntityMaid maid, EntityMaid target) {
        if (!target.isAlive()) {
            maid.setTarget(null);
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }

        if (isWithinMeleeRange(maid, target)) {
            // 只有目标有主人时才触发求救
            if (target.isTame() && target.getOwner() != null) {
                onVictimAttackedByBetrayer(target, maid);
            }

            maid.swing(InteractionHand.MAIN_HAND);

            float baseDamage = (float) (maid.getAttribute(Attributes.ATTACK_DAMAGE) != null ?
                    maid.getAttribute(Attributes.ATTACK_DAMAGE).getValue() : 0.0);
            float finalDamage = Math.max(1.0f, baseDamage);

            target.hurt(target.damageSources().mobAttack(maid), finalDamage);

            maid.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.0f);
            attackCooldown.put(maid.getUUID(), ATTACK_COOLDOWN_TICKS);

            if (!target.isAlive()) {
                maid.setTarget(null);
                maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        } else if (!BetrayalOutpostStackManager.isMovementDelegated(maid)) {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(target.blockPosition()), ATTACK_SPEED, 1));
        }
    }

    /** 只改变复仇女仆现有合法目标的选择节奏；真实背叛女仆保持原样。 */
    private static LivingEntity findClearlyCloserOutpostTarget(EntityMaid maid, LivingEntity current) {
        double currentDistance = maid.distanceToSqr(current);
        return maid.level().getEntitiesOfClass(LivingEntity.class,
                        BetrayalOutpostMaidData.pursuitSearchArea(maid),
                        candidate -> candidate != maid && candidate != current
                                && BetrayalOutpostAlertManager.isBaseValidTarget(maid, candidate))
                .stream().sorted(java.util.Comparator.comparingDouble(maid::distanceToSqr))
                .limit(6)
                .filter(candidate -> BetrayalOutpostAlertManager.canKeepOrDetectTarget(maid, candidate))
                .filter(candidate -> {
                    double distance = maid.distanceToSqr(candidate);
                    return distance + OUTPOST_RETARGET_MIN_GAIN_SQR < currentDistance
                            && distance < currentDistance * OUTPOST_RETARGET_DISTANCE_RATIO_SQR;
                })
                .min(java.util.Comparator.comparingDouble(maid::distanceToSqr))
                .orElse(null);
    }

    /** 据点女仆使用与现有背叛攻击相同的节奏攻击附近其他生物。 */
    private static void performAttackOnLiving(EntityMaid maid, LivingEntity target) {
        if (!target.isAlive() || BetrayalOutpostMaidData.areSisters(maid, target)) {
            maid.setTarget(null);
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }
        if (isWithinMeleeRange(maid, target)) {
            maid.swing(InteractionHand.MAIN_HAND);
            float baseDamage = (float) (maid.getAttribute(Attributes.ATTACK_DAMAGE) == null
                    ? 0.0D : maid.getAttribute(Attributes.ATTACK_DAMAGE).getValue());
            target.hurt(target.damageSources().mobAttack(maid), Math.max(1.0F, baseDamage));
            maid.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 0.8F, 1.0F);
            attackCooldown.put(maid.getUUID(), ATTACK_COOLDOWN_TICKS);
        }
    }

    /** 据点女仆即使处在攻击冷却或扩展攻击距离内，也继续使用原有 Brain 寻路压向目标。 */
    private static boolean maintainOutpostPursuit(EntityMaid maid, LivingEntity target) {
        CompoundTag data = maid.getPersistentData();
        long now = maid.level().getGameTime();
        if (!data.contains(OUTPOST_PURSUIT_LAST_POS, CompoundTag.TAG_LONG)
                || !data.hasUUID(OUTPOST_PURSUIT_TARGET)
                || !data.getUUID(OUTPOST_PURSUIT_TARGET).equals(target.getUUID())) {
            data.putUUID(OUTPOST_PURSUIT_TARGET, target.getUUID());
            data.putLong(OUTPOST_PURSUIT_LAST_POS, maid.blockPosition().asLong());
            data.putLong(OUTPOST_PURSUIT_PROGRESS_AT, now);
            data.putLong(OUTPOST_PURSUIT_HIT_AT, now);
            data.remove("CallResponseOutpostPursuitDistance");
            data.remove("CallResponseOutpostPursuitPathFailures");
        }
        if (maid.tickCount % 20 == 0 && data.contains(OUTPOST_PURSUIT_LAST_POS, CompoundTag.TAG_LONG)) {
            BlockPos previous = BlockPos.of(data.getLong(OUTPOST_PURSUIT_LAST_POS));
            if (previous.distSqr(maid.blockPosition()) >= 4.0D) {
                data.putLong(OUTPOST_PURSUIT_LAST_POS, maid.blockPosition().asLong());
                data.putLong(OUTPOST_PURSUIT_PROGRESS_AT, now);
                data.remove(OUTPOST_NO_TARGET_SINCE);
            }
        }
        if (canAttackOutpostTargetNow(maid, target)) {
            data.putLong(OUTPOST_PURSUIT_PROGRESS_AT, now);
            data.remove(OUTPOST_NO_TARGET_SINCE);
        }
        if (now - Math.max(data.getLong(OUTPOST_PURSUIT_PROGRESS_AT),
                data.getLong(OUTPOST_PURSUIT_HIT_AT)) >= OUTPOST_STUCK_TICKS) {
            data.putUUID(OUTPOST_BLOCKED_TARGET, target.getUUID());
            data.putLong(OUTPOST_BLOCKED_UNTIL, now + OUTPOST_BLOCKED_TICKS);
            BetrayalOutpostAlertManager.clearCombat(maid);
            data.remove(OUTPOST_PURSUIT_TARGET);
            return false;
        }
        if (BetrayalOutpostStackManager.isMovementDelegated(maid)
                || canAttackOutpostTargetNow(maid, target)) return true;
        if (isTlmMeleeActive(maid)) {
            // TaskAttack owns ordinary chase. Only bridge its extended-reach/blocked-sight gap.
            if (!maid.getSensing().hasLineOfSight(target) && maid.tickCount % 20 == 0
                    && !maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new EntityTracker(target, false), ATTACK_SPEED, 1));
            }
        } else if (maid.tickCount % 20 == 0
                || !maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            // Farm/feed and off-duty maids have no TLM work-attack chase behavior.
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(target, false), ATTACK_SPEED, 1));
        }
        return true;
    }

    private static boolean isTlmMeleeActive(EntityMaid maid) {
        return maid.getTask() instanceof TaskAttack
                && maid.getBrain().getActiveNonCoreActivity().orElse(null) == Activity.WORK;
    }

    private static boolean canAttackOutpostTargetNow(EntityMaid maid, LivingEntity target) {
        if (!maid.getSensing().hasLineOfSight(target)) return false;
        if (maid.getTask() instanceof IRangedAttackTask) {
            return maid.distanceToSqr(target) <= 16.0D * 16.0D;
        }
        return isWithinMeleeRange(maid, target);
    }

    @SubscribeEvent
    public void onOutpostDamageProgress(LivingDamageEvent event) {
        if (event.getAmount() <= 0 || !(event.getSource().getEntity() instanceof EntityMaid maid)
                || !BetrayalOutpostMaidData.isOutpostMaid(maid)
                || maid.getTarget() != event.getEntity()) return;
        maid.getPersistentData().putLong(OUTPOST_PURSUIT_HIT_AT, maid.level().getGameTime());
        maid.getPersistentData().remove(OUTPOST_NO_TARGET_SINCE);
    }

    public static boolean isTemporarilyUnreachable(EntityMaid maid, LivingEntity target) {
        CompoundTag data = maid.getPersistentData();
        return data.hasUUID(OUTPOST_BLOCKED_TARGET) && data.getUUID(OUTPOST_BLOCKED_TARGET).equals(target.getUUID())
                && maid.level().getGameTime() < data.getLong(OUTPOST_BLOCKED_UNTIL);
    }

    private static boolean isInvalidTarget(EntityMaid maid, LivingEntity target) {
        if (!target.isAlive()) return true;
        if (!BetrayalOutpostMaidData.isOutpostMaid(maid)) return false;
        return !BetrayalOutpostAlertManager.isBaseValidTarget(maid, target);
    }

    private static boolean isWithinMeleeRange(EntityMaid maid, LivingEntity target) {
        double distance = maid.distanceToSqr(target);
        if (!BetrayalOutpostMaidData.isOutpostMaid(maid)) return distance < 4.0D;
        // 复用 TLM 的三级好感近战距离；扩展部分要求视线，避免隔墙造成远距离伤害。
        return distance < maid.getMeleeAttackRangeSqr(target)
                && maid.getSensing().hasLineOfSight(target);
    }

    private static boolean isInsideOutpost(EntityMaid maid, LivingEntity entity) {
        return BetrayalOutpostMaidData.isWithinPursuitArea(maid, entity);
    }

    public static boolean isReturningToOutpost(EntityMaid maid) {
        return BetrayalOutpostMaidData.isOutpostMaid(maid)
                && maid.getTarget() == null
                && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !BetrayalOutpostAlertManager.isRestingOrSeekingRest(maid)
                && maid.getPersistentData().getBoolean(OUTPOST_RETURN_NEEDED);
    }

    private static void updateOutpostReturn(EntityMaid maid) {
        // 归位是离营地后的单次兜底，不能抢休息日程或在正常营地活动时循环启动。
        maid.setAggressive(false);
        if (BetrayalOutpostAlertManager.isRestingOrSeekingRest(maid)
                || BetrayalOutpostMaidData.isMaidWithinReturnArea(maid)
                || BetrayalOutpostMaidData.recordedHome(maid) == null
                || isOutpostReturnMovementUnavailable(maid)) {
            cancelOutpostReturn(maid);
            return;
        }
        maid.getPersistentData().putBoolean(OUTPOST_RETURN_NEEDED, true);
        returnToOutpost(maid);
    }

    private static boolean isOutpostReturnMovementUnavailable(EntityMaid maid) {
        return BetrayalOutpostStackManager.isMovementDelegated(maid)
                || maid.isPassenger() || !maid.getPassengers().isEmpty()
                || MaidMovementControl.controlsOther(maid, MaidMovementControl.Reason.BETRAYAL,
                MaidMovementControl.Field.PATH);
    }

    private static void cancelOutpostReturn(EntityMaid maid) {
        stopReturningToOutpost(maid);
        CompoundTag data = maid.getPersistentData();
        data.remove(OUTPOST_RETURN_NEEDED);
        data.remove(OUTPOST_NO_TARGET_SINCE);
    }

    private static void returnToOutpost(EntityMaid maid) {
        BlockPos home = BetrayalOutpostMaidData.recordedHome(maid);
        if (home == null || BetrayalOutpostAlertManager.isRestingOrSeekingRest(maid)
                || BetrayalOutpostMaidData.isMaidWithinReturnArea(maid)
                || isOutpostReturnMovementUnavailable(maid)) {
            cancelOutpostReturn(maid);
            return;
        }
        CompoundTag data = maid.getPersistentData();
        long now = maid.level().getGameTime();
        maid.setAggressive(false);
        if (!data.contains(OUTPOST_NO_TARGET_SINCE, CompoundTag.TAG_LONG)) {
            data.putLong(OUTPOST_NO_TARGET_SINCE, now);
        }

        if (now - data.getLong(OUTPOST_NO_TARGET_SINCE) >= OUTPOST_TELEPORT_AFTER
                && maid.tickCount % 20 == 0) {
            // 传送当刻再次核对：旧倒计时不得把已开始休息或新进入战斗的女仆带走。
            LivingEntity current = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                    .orElse(maid.getTarget());
            if (BetrayalOutpostAlertManager.isRestingOrSeekingRest(maid)
                    || BetrayalOutpostMaidData.isMaidWithinReturnArea(maid)
                    || BetrayalOutpostAlertManager.canKeepOrDetectTarget(maid, current)
                    || isOutpostReturnMovementUnavailable(maid)) {
                cancelOutpostReturn(maid);
                return;
            }
            BlockPos safe = chooseSafeTeleportPosition(maid, home);
            if (safe != null) {
                cancelOutpostReturn(maid);
                maid.setDeltaMovement(Vec3.ZERO);
                maid.fallDistance = 0;
                maid.teleportTo(safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D);
                return;
            }
        }

        BlockPos destination = data.contains(OUTPOST_RETURN_POS, CompoundTag.TAG_LONG)
                ? BlockPos.of(data.getLong(OUTPOST_RETURN_POS)) : null;
        if (destination != null && !isSafeReturnPosition(maid, home, destination)) {
            stopReturningToOutpost(maid);
            destination = null;
        }
        if (destination != null) {
            double distance = maid.distanceToSqr(destination.getX() + 0.5D,
                    destination.getY(), destination.getZ() + 0.5D);
            if (distance + 1.0D < data.getDouble(OUTPOST_RETURN_LAST_DISTANCE)) {
                data.putDouble(OUTPOST_RETURN_LAST_DISTANCE, distance);
                data.putLong(OUTPOST_RETURN_RETRY_AT, now + 40);
            }
        }
        if (now >= data.getLong(OUTPOST_RETURN_RETRY_AT)) {
            if (destination != null) stopReturningToOutpost(maid);
            destination = chooseReturnPosition(maid, home);
            data.putLong(OUTPOST_RETURN_RETRY_AT, now + 40);
            if (destination != null) {
                data.putLong(OUTPOST_RETURN_POS, destination.asLong());
                data.putDouble(OUTPOST_RETURN_LAST_DISTANCE,
                        maid.distanceToSqr(destination.getX() + 0.5D,
                                destination.getY(), destination.getZ() + 0.5D));
            }
        }
        if (destination == null) return; // 水中暂时无可达路径时保持归位状态，下次继续尝试。
        BlockPos returnDestination = destination;
        boolean alreadyWalking = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(walk -> walk.getTarget().currentBlockPosition().equals(returnDestination)).orElse(false);
        if (!alreadyWalking || maid.tickCount % 20 == 0) {
            // 沿用现有战斗与回营地使用的 Brain WALK_TARGET，不另造移动控制。
            WalkTarget walkTarget = new WalkTarget(new BlockPosTracker(destination), ATTACK_SPEED, 2);
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
            outpostReturnWalkTargets.put(maid, walkTarget);
        }
    }

    private static BlockPos chooseReturnPosition(EntityMaid maid, BlockPos home) {
        Set<BlockPos> checked = new HashSet<>();
        int pathChecks = 0;
        // 优先真实营地地面，同层无路时再考虑附近台阶；不优先钻向平台下方。
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            for (int attempt = 0; attempt < 4 && pathChecks < 20; attempt++) {
                BlockPos candidate = attempt == 3 ? home.offset(0, dy, 0) : home.offset(
                        maid.getRandom().nextInt(9) - 4, dy, maid.getRandom().nextInt(9) - 4);
                if (!checked.add(candidate) || !isSafeReturnPosition(maid, home, candidate)
                        || !maid.getNavigation().isStableDestination(candidate)) continue;
                pathChecks++;
                Path path = maid.getNavigation().createPath(candidate, 0);
                if (path != null && path.canReach() && path.getEndNode() != null
                        && path.getEndNode().y == candidate.getY()) return candidate;
            }
        }
        return null;
    }

    private static BlockPos chooseSafeTeleportPosition(EntityMaid maid, BlockPos home) {
        int[][] offsets = {{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3},
                {3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            int start = maid.getRandom().nextInt(offsets.length);
            for (int i = 0; i < offsets.length; i++) {
                int[] offset = offsets[(start + i) % offsets.length];
                BlockPos candidate = home.offset(offset[0], dy, offset[1]);
                if (!isSafeReturnPosition(maid, home, candidate)) continue;
                AABB landing = maid.getBoundingBox().move(candidate.getX() + 0.5D - maid.getX(),
                        candidate.getY() - maid.getY(), candidate.getZ() + 0.5D - maid.getZ());
                if (maid.level().noCollision(maid, landing)
                        && maid.level().getEntities(maid, landing, Entity::isAlive).isEmpty()) return candidate;
            }
        }
        return null;
    }

    private static boolean isSafeReturnPosition(EntityMaid maid, BlockPos home, BlockPos pos) {
        if (Math.abs(pos.getY() - home.getY()) > 2
                || Math.abs(pos.getX() - home.getX()) > 5
                || Math.abs(pos.getZ() - home.getZ()) > 5
                || !maid.level().hasChunkAt(pos)) return false;
        BlockPos floor = pos.below();
        return maid.level().getFluidState(floor).isEmpty()
                && maid.level().getBlockState(floor).isFaceSturdy(maid.level(), floor, Direction.UP)
                && maid.level().getBlockState(pos).isAir()
                && maid.level().getBlockState(pos.above()).isAir()
                && maid.level().getFluidState(pos).isEmpty()
                && maid.level().getFluidState(pos.above()).isEmpty();
    }

    private static void stopReturningToOutpost(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        data.remove(OUTPOST_RETURN_RETRY_AT);
        data.remove(OUTPOST_RETURN_LAST_DISTANCE);
        data.remove(OUTPOST_RETURN_POS);
        WalkTarget ownedTarget = outpostReturnWalkTargets.remove(maid);
        if (ownedTarget == null) return;
        if (maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(walk -> walk == ownedTarget).orElse(false)) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
            maid.getNavigation().stop();
        }
    }

    // ===== 破坏箱子 =====
    private static void destroyNearbyContainer(EntityMaid maid) {
        BlockPos pos = maid.blockPosition();
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos targetPos = pos.offset(dx, dy, dz);
                    BlockState state = maid.level().getBlockState(targetPos);
                    Block block = state.getBlock();
                    if (block instanceof ChestBlock || block instanceof BarrelBlock) {
                        destroyBlock(maid, targetPos);
                        return;
                    }
                }
            }
        }
    }

    private static void destroyBlock(EntityMaid maid, BlockPos pos) {
        Level level = maid.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        BlockEntity be = level.getBlockEntity(pos);
        Block.dropResources(state, level, pos, be);
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        maid.playSound(SoundEvents.STONE_BREAK, 0.8f, 1.0f);
    }

    // ===== 爆炸 =====
    private static void triggerExplosion(EntityMaid maid) {
        Level level = maid.level();
        if (level.isClientSide) return;

        Vec3 pos = maid.position();
        Level.ExplosionInteraction interaction = BroadcastConfig.BETRAYAL_MAID_EXPLOSION_BREAK_BLOCKS.get()
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;
        level.explode(null, pos.x, pos.y, pos.z, EXPLOSION_POWER, interaction);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        maid.playSound(SoundEvents.GENERIC_EXPLODE, 2.0f, 1.0f);
    }

    // ===== 监听女仆死亡 =====
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMaidDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        UUID maidId = maid.getUUID();
        suppressedDanger.remove(maidId);

        if (isBetraying(maid)) {
            MaidMovementControl.abortAll(maid, MaidMovementControl.AbortCause.DEATH);
            // TLM creates a tombstone whenever Owner UUID is still present in dropEquipment().
            // The one-shot death after intimidation sets the betrayal marker without entering
            // the normal betrayal transition, so clear ownership on confirmed death as well.
            if (!maid.level().isClientSide) ensureUntamed(maid);
            if (!BetrayalOutpostMaidData.isOutpostMaid(maid)) {
                triggerExplosion(maid);
            }
            isBetraying.remove(maidId);
            maid.getPersistentData().remove(BETRAYAL_NBT_TAG);
            dangerTimer.remove(maidId);
            attackCooldown.remove(maidId);
            attackCountMap.remove(maidId);
            lastReplyTimeMap.remove(maidId);
        }
        VictimFleeState flee = victimFleeStates.remove(maidId);
        if (flee != null) MaidMovementControl.end(maid, MaidMovementControl.Reason.BETRAYAL_VICTIM_FLEE);
    }

    // ===== 重置 =====
    public static void resetBetrayal(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        isBetraying.remove(maidId);
        maid.getPersistentData().remove(BETRAYAL_NBT_TAG);
        dangerTimer.remove(maidId);
        suppressedDanger.remove(maidId);
        attackCooldown.remove(maidId);
        attackCountMap.remove(maidId);
        lastReplyTimeMap.remove(maidId);
        maid.setTarget(null);
        maid.setAggressive(false);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        MaidMovementControl.end(maid, MaidMovementControl.Reason.BETRAYAL);
    }

    @SubscribeEvent
    public void onMaidLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            suppressedDanger.remove(maid.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        suppressedDanger.clear();
    }
}
