package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionBetrayalManager {

    // ===== 配置参数 =====
    private static final int TRUST_THRESHOLD = 10;
    private static final int FEAR_THRESHOLD = 90;
    private static final int DURATION_THRESHOLD = 200;      // 30 秒
    private static final float EXPLOSION_POWER = 6.0f;
    private static final float ATTACK_SPEED = 0.6f;
    private static final int SEARCH_RADIUS = 10;
    private static final int ATTACK_COOLDOWN_TICKS = 10;
    private static final int BETRAYAL_REPLY_COOLDOWN = 100;  // 3 秒冷却
    private static final String BETRAYAL_NBT_TAG = "IsBetraying";

    // ===== 状态存储 =====
    private static final Map<UUID, Integer> dangerTimer = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> isBetraying = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> attackCooldown = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> attackCountMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastReplyTimeMap = new ConcurrentHashMap<>();

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

    // ===== 处理受害者被背叛女仆攻击 =====
    public static void onVictimAttackedByBetrayer(EntityMaid victim, EntityMaid betrayer) {
        // 只有有主人的女仆才会触发求救（野生女仆不触发）
        if (!victim.isTame() || victim.getOwner() == null) return;
        if (victim.level().isClientSide) return;

        UUID victimId = victim.getUUID();
        long now = victim.level().getGameTime();
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
                String victimName = victim.getCustomName() != null ? victim.getCustomName().getString() : "女仆";
                ownerPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[警告] 你的女仆 " + victimName + " 正在被一个疯狂的女仆攻击！"
                ));
            }
            MaidResponder.processBroadcast(ownerPlayer, Collections.singletonList(victim), instruction, false);
        } else {
            victim.getChatBubbleManager().addTextChatBubble("谁来救救我...");
        }

        // 强制逃跑（不反击）
        victim.setTarget(null);
        victim.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        victim.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        victim.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);

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
        victim.setInSittingPose(true);
        lastReplyTimeMap.put(victimId, now);
    }

    // ===== 定时检查 =====
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            player.level().getEntitiesOfClass(EntityMaid.class,
                            player.getBoundingBox().inflate(32))
                    .forEach(maid -> {
                        UUID maidId = maid.getUUID();

                        // 检查是否已背叛
                        if (isBetraying(maid)) {
                            performBetrayalActions(maid);
                            return;
                        }

                        // 只有有主人的女仆才能触发新背叛
                        if (!maid.isTame() || maid.getOwner() == null) return;

                        EmotionData.EmotionValues values = EmotionData.get(maid, player);
                        int trust = values.trust();
                        int fear = values.fear();

                        if (trust < TRUST_THRESHOLD && fear > FEAR_THRESHOLD) {
                            int timer = dangerTimer.getOrDefault(maidId, 0) + 1;
                            dangerTimer.put(maidId, timer);

                            if (timer >= DURATION_THRESHOLD) {
                                triggerBetrayal(maid, player);
                            }
                        } else {
                            dangerTimer.put(maidId, 0);
                        }
                    });
        }
    }

    // ===== 触发背叛 =====
    private static void triggerBetrayal(EntityMaid maid, ServerPlayer player) {
        UUID maidId = maid.getUUID();
        if (isBetraying(maid)) return;

        isBetraying.put(maidId, true);
        maid.getPersistentData().putBoolean(BETRAYAL_NBT_TAG, true); // 持久化

        dangerTimer.remove(maidId);
        attackCooldown.remove(maidId);

        if (maid.isInSittingPose()) maid.setInSittingPose(false);
        maid.setOwnerUUID(null);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);

        ResourceLocation attackTaskId = ResourceLocation.parse("touhou_little_maid:attack");
        TaskManager.findTask(attackTaskId).ifPresent(maid::setTask);

        maid.setAggressive(true);

        String maidName = maid.getName().getString();
        if (player != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c§l" + maidName + " 背叛了你！她开始疯狂攻击！"
            ));
        }
        maid.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
    }

    // ===== 执行背叛行为 =====
    private static void performBetrayalActions(EntityMaid maid) {
        if (!maid.isAlive()) return;

        UUID maidId = maid.getUUID();

        // 确保内存状态一致（如果内存中不存在但NBT存在，恢复）
        if (!isBetraying.containsKey(maidId) && maid.getPersistentData().contains(BETRAYAL_NBT_TAG)) {
            boolean state = maid.getPersistentData().getBoolean(BETRAYAL_NBT_TAG);
            if (state) {
                isBetraying.put(maidId, true);
            }
        }

        int cooldown = attackCooldown.getOrDefault(maidId, 0);
        if (cooldown > 0) {
            attackCooldown.put(maidId, cooldown - 1);
            return;
        }

        LivingEntity target = maid.getTarget();

        if (target == null || !target.isAlive()) {
            target = selectTarget(maid);
            if (target != null) {
                maid.setTarget(target);
                maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
            } else {
                destroyNearbyContainer(maid);
                return;
            }
        }

        if (target instanceof ServerPlayer player) {
            performAttackOnPlayer(maid, player);
        } else if (target instanceof EntityMaid otherMaid) {
            performAttackOnMaid(maid, otherMaid);
        } else {
            maid.setTarget(null);
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    // ===== 选择目标（优先玩家，其次任何女仆，不检查主人） =====
    private static LivingEntity selectTarget(EntityMaid maid) {
        LivingEntity nearestPlayer = findNearestPlayer(maid);
        if (nearestPlayer != null) return nearestPlayer;

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
                maid.getBoundingBox().inflate(SEARCH_RADIUS));
        LivingEntity nearest = null;
        double minDist = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            if (p.isAlive() && !p.isSpectator()) {
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
        if (!player.isAlive()) {
            maid.setTarget(null);
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            return;
        }

        double distance = maid.distanceTo(player);
        if (distance < 2.0) {
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
        } else {
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

        double distance = maid.distanceTo(target);
        if (distance < 2.0) {
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
        } else {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(target.blockPosition()), ATTACK_SPEED, 1));
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
        level.explode(null, pos.x, pos.y, pos.z, EXPLOSION_POWER, Level.ExplosionInteraction.TNT);

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        maid.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0f, 1.0f);
    }

    // ===== 监听女仆死亡 =====
    @SubscribeEvent
    public void onMaidDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        UUID maidId = maid.getUUID();

        if (isBetraying(maid)) {
            triggerExplosion(maid);
            isBetraying.remove(maidId);
            maid.getPersistentData().remove(BETRAYAL_NBT_TAG);
            dangerTimer.remove(maidId);
            attackCooldown.remove(maidId);
            attackCountMap.remove(maidId);
            lastReplyTimeMap.remove(maidId);
        }
    }

    // ===== 重置 =====
    public static void resetBetrayal(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        isBetraying.remove(maidId);
        maid.getPersistentData().remove(BETRAYAL_NBT_TAG);
        dangerTimer.remove(maidId);
        attackCooldown.remove(maidId);
        attackCountMap.remove(maidId);
        lastReplyTimeMap.remove(maidId);
        maid.setTarget(null);
        maid.setAggressive(false);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }
}