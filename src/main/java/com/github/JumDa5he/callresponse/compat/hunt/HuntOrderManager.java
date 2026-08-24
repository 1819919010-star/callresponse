package com.github.JumDa5he.callresponse.compat.hunt;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.api.task.IAttackTask;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.github.tartaricacid.touhoulittlemaid.util.TaskEquipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 狩猎令核心逻辑。
 * 触发：目标进入死忠女仆 15 格范围。
 * 时序：触发 → 起立+解除跟随+换任务/备武器 → 追击（WALK_TARGET）→ 原版攻击；空手时自动空手近战。
 * 结束：目标死亡→自动从名单删除该条目；目标被移出名单→立即停止；目标离开范围→停止但保留条目。
 */
public class HuntOrderManager {

    // ===== 配置参数 =====
    private static final double HUNT_RANGE_SQR = 15.0 * 15.0;
    private static final double CHASE_DIALOGUE_RANGE_SQR = 12.0 * 12.0;
    private static final double MELEE_RANGE_SQR = 2.5 * 2.5;
    private static final int SEARCH_TICKS = 20;
    private static final int SUICIDE_WAIT_TICKS = 200;      // 10 秒
    private static final int DIALOGUE_COOLDOWN_TICKS = 600; // 30 秒
    private static final int MELEE_COOLDOWN_TICKS = 20;     // 空手攻击间隔（与原版一致）
    private static final ResourceLocation ATTACK_TASK_ID = ResourceLocation.parse("touhou_little_maid:attack");
    private static final ResourceLocation GUN_ATTACK_TASK_ID = ResourceLocation.parse("touhou_little_maid:gun_attack");

    // ===== 状态存储 =====
    private static final Map<UUID, ActiveHunt> activeHunts = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> suicideTickets = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastDialogueTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> meleeCooldown = new ConcurrentHashMap<>();
    private static final Map<UUID, ForcedDamage> pendingForcedDamage = new ConcurrentHashMap<>();

    // ===== 狩猎目标"保护破除"锁定（引用计数：支持多只女仆同时锁定同一目标） =====
    private static final Map<UUID, Integer> huntLockCount = new ConcurrentHashMap<>();

    private static class ActiveHunt {
        final EntityMaid maid;
        final UUID targetId;
        final String targetName;
        final com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask prevTask;
        final boolean homeModeWas;
        final boolean sittingWas;
        final UUID targetPlayerUuid;
        final BlockPos lastTargetPos;
        final boolean protectionBypassTarget;
        GameType prevGameMode;
        boolean taskChanged;
        boolean targetDied;
        boolean hasUsableWeapon;
        boolean manualMeleeWeapon;

        ActiveHunt(EntityMaid maid, LivingEntity target, String targetName) {
            this.maid = maid;
            this.targetId = target.getUUID();
            this.targetName = targetName;
            this.prevTask = maid.getTask();
            this.homeModeWas = maid.isHomeModeEnable();
            this.sittingWas = maid.isInSittingPose();
            this.lastTargetPos = target.blockPosition();
            this.protectionBypassTarget = needsProtectionBypass(target);
            if (target instanceof ServerPlayer player) {
                this.targetPlayerUuid = player.getUUID();
                this.prevGameMode = player.gameMode.getGameModeForPlayer();
            } else {
                this.targetPlayerUuid = null;
            }
        }
    }

    /** 同一 tick 内的多次枪击会合并，最终按原始伤害总和直接校正生命值。 */
    private static class ForcedDamage {
        final float healthBefore;
        UUID attackerId;
        DamageSource source;
        float damage;
        boolean nativeHurtSucceeded;
        boolean explicitGunHit;

        ForcedDamage(float healthBefore, float damage, UUID attackerId, DamageSource source) {
            this.healthBefore = healthBefore;
            this.damage = damage;
            this.attackerId = attackerId;
            this.source = source;
        }

        void add(float amount, UUID attackerId, DamageSource source) {
            damage = Math.min(Float.MAX_VALUE, damage + amount);
            this.attackerId = attackerId;
            this.source = source;
        }
    }

    // ===== 查询 =====
    public static boolean isHunting(EntityMaid maid) {
        return activeHunts.containsKey(maid.getUUID());
    }

    public static boolean hasActiveHunts() {
        return !activeHunts.isEmpty();
    }

    public static boolean isHuntTarget(EntityMaid maid, Entity target) {
        ActiveHunt hunt = activeHunts.get(maid.getUUID());
        return hunt != null && hunt.targetId.equals(target.getUUID());
    }

    /**
     * 该实体是否正被（任意女仆）作为狩猎目标锁定 → 保护破除生效。
     * 只有存在引用计数时返回 true，不是狩猎目标时完全恢复正常。
     */
    public static boolean isHuntLockedTarget(Entity entity) {
        if (entity.level().isClientSide) return false;
        Integer count = huntLockCount.get(entity.getUUID());
        return count != null && count > 0;
    }


    public static boolean needsProtectionBypass(Entity entity) {
        return entity instanceof LivingEntity;
    }

    public static boolean needsDirectHealthSettlement(Entity entity) {
        return entity instanceof LivingEntity;
    }

    public static boolean needsForcedDeathSettlement(Entity entity) {
        return entity instanceof Player || entity instanceof EntityMaid;
    }

    public static boolean isProtectionBypassTarget(Entity entity) {
        return needsProtectionBypass(entity) && isHuntLockedTarget(entity);
    }

    public static boolean isHuntDamage(Entity target, DamageSource source) {
        if (!isProtectionBypassTarget(target)) return false;
        return findHuntingMaid(target, source) != null;
    }

    public static EntityMaid findHuntingMaid(Entity target, DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker instanceof EntityMaid maid && isHuntTarget(maid, target)) {
            return maid;
        }
        Entity direct = source.getDirectEntity();
        if (direct instanceof EntityMaid maid && isHuntTarget(maid, target)) {
            return maid;
        }
        if (direct instanceof Projectile projectile
                && projectile.getOwner() instanceof EntityMaid maid
                && isHuntTarget(maid, target)) {
            return maid;
        }
        return null;
    }


    public static void queueForcedHit(LivingEntity target, DamageSource source, float rawDamage) {
        float damage = HuntDamageContext.normalizeDamage(rawDamage);
        if (!needsDirectHealthSettlement(target) || !isHuntDamage(target, source) || damage <= 0) return;
        EntityMaid maid = findHuntingMaid(target, source);
        if (maid != null) {
            pendingForcedDamage.compute(target.getUUID(), (uuid, pending) -> {
                if (pending == null) {
                    return new ForcedDamage(target.getHealth(), damage, maid.getUUID(), source);
                }
                // 枪械命中前事件已经登记了完整伤害；随后进入 hurt 的分段伤害只用于播放原生效果，不能重复相加。
                if (pending.explicitGunHit) {
                    pending.attackerId = maid.getUUID();
                    pending.source = source;
                    return pending;
                }
                pending.add(damage, maid.getUUID(), source);
                return pending;
            });
            HuntDamageContext.markForcedHitQueued();
        }
    }


    public static void queueExplicitGunHit(EntityMaid maid, LivingEntity target,
                                           DamageSource source, float rawDamage) {
        float damage = HuntDamageContext.normalizeDamage(rawDamage);
        if (target.level().isClientSide || source == null || damage <= 0.0F
                || !needsDirectHealthSettlement(target) || !isHuntTarget(maid, target)) {
            return;
        }

        pendingForcedDamage.compute(target.getUUID(), (uuid, pending) -> {
            if (pending == null) {
                pending = new ForcedDamage(target.getHealth(), damage, maid.getUUID(), source);
            } else {
                pending.add(damage, maid.getUUID(), source);
            }
            pending.explicitGunHit = true;
            return pending;
        });
        HuntDamageContext.markForcedHitQueued();
    }

    /** 记录原版 hurt 链是否真正成功，避免正常命中时重复播放受击与击退。 */
    public static void recordNativeHurtResult(LivingEntity target, DamageSource source, boolean succeeded) {
        if (!succeeded || !isHuntDamage(target, source)) return;
        ForcedDamage pending = pendingForcedDamage.get(target.getUUID());
        if (pending != null) {
            pending.nativeHurtSucceeded = true;
        }
    }

    // ===== 狩猎锁定（引用计数） =====
    private static void applyHuntLock(Level level, BlockPos pos, UUID targetId) {
        int c = huntLockCount.merge(targetId, 1, Integer::sum);
        if (c == 1) {
            playHuntSound(level, pos, true);
        }
    }

    private static void releaseHuntLock(Level level, BlockPos pos, UUID targetId) {
        Integer c = huntLockCount.get(targetId);
        if (c == null || c <= 1) {
            if (huntLockCount.remove(targetId) != null) {
                playHuntSound(level, pos, false);
            }
        } else {
            huntLockCount.put(targetId, c - 1);
        }
    }

    // ===== 锁定/解除音效（便于辨认保护破除状态） =====
    private static void playHuntSound(Level level, BlockPos pos, boolean lock) {
        SoundEvent sound = lock ? SoundEvents.GLASS_BREAK : SoundEvents.IRON_TRAPDOOR_OPEN;
        float pitch = lock ? 0.85f : 1.2f;
        level.playSound(null, pos, sound, SoundSource.NEUTRAL, 0.85f, pitch);
    }

    // ===== 主循环 =====
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        long gameTime = server.overworld().getGameTime();


        processForcedDamage(server);

        // 1. 每 tick：维持进行中的狩猎
        for (ActiveHunt hunt : new ArrayList<>(activeHunts.values())) {
            tickActiveHunt(hunt, server);
        }

        // 2. 每 tick：处理自尽彩蛋（坐下等 10 秒后直接移除实体）
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(suicideTickets.entrySet())) {
            Entity e = resolveEntity(server, entry.getKey());
            if (e instanceof EntityMaid maid) {
                if (gameTime >= entry.getValue()) {
                    suicideTickets.remove(entry.getKey());
                    HuntOrderData.removeSelf(maid);
                    ActiveHunt hunt = activeHunts.remove(maid.getUUID());
                    if (hunt != null && hunt.protectionBypassTarget) {
                        releaseHuntLock(maid.level(), hunt.lastTargetPos, hunt.targetId);
                    }
                    lastDialogueTime.remove(maid.getUUID());
                    meleeCooldown.remove(maid.getUUID());
                    maid.setInSittingPose(true);
                    maid.remove(Entity.RemovalReason.KILLED);
                }
            } else {
                suicideTickets.remove(entry.getKey());
            }
        }

        // 3. 每 20 tick：扫描触发新狩猎
        if (gameTime % SEARCH_TICKS == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getEntities().getAll()) {
                    if (entity instanceof EntityMaid maid && !isHunting(maid)) {
                        tryFindHuntTarget(maid, server);
                    }
                }
            }
        }
    }

    // ===== 触发扫描（只有靠近死忠女仆 15 格才触发） =====
    private void tryFindHuntTarget(EntityMaid maid, MinecraftServer server) {
        if (maid.level().isClientSide) return;
        if (!maid.isTame() || maid.getOwnerUUID() == null) return;

        List<HuntOrderEntry> entries = HuntOrderData.getEntries(maid);
        if (entries.isEmpty()) return;

        // 狩猎优先级最高：背叛女仆让位
        if (EmotionBetrayalManager.isBetraying(maid)) {
            EmotionBetrayalManager.resetBetrayal(maid);
        }

        for (HuntOrderEntry entry : entries) {
            // 彩蛋 1：名单里有女仆自己的 UUID → 坐下 → 对话 → 10 秒后自杀
            if (entry.uuid.equals(maid.getUUID())) {
                triggerSelfSuicide(maid);
                return;
            }

            Entity entity = resolveEntity(server, entry.uuid);
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                continue;
            }
            if (!entity.level().equals(maid.level())) {
                continue;
            }
            // 只判定：目标靠近死忠女仆 15 格
            if (target.distanceToSqr(maid) <= HUNT_RANGE_SQR) {
                startHunt(maid, target, entry);
                return;
            }
        }
    }

    // ===== 开始狩猎（触发时只起身+解除跟随+备战，不说话） =====
    private void startHunt(EntityMaid maid, LivingEntity target, HuntOrderEntry entry) {
        if (isHunting(maid)) return;

        ActiveHunt hunt = new ActiveHunt(maid, target, entry.name);
        activeHunts.put(maid.getUUID(), hunt);
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.HUNT,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH,
                        MaidMovementControl.Field.SCHEDULE, MaidMovementControl.Field.POSE,
                        MaidMovementControl.Field.TASK));

        if (hunt.protectionBypassTarget) {
            applyHuntLock(maid.level(), target.blockPosition(), target.getUUID());
        }

        // 起立
        maid.setInSittingPose(false);

        // 枪械优先，其次近战武器；两者都没有时收起主手物品并进入空手追击。
        hunt.hasUsableWeapon = configureCombatLoadout(hunt);
        // home/schedule 保持原值；统一控制器暂停 SchedulePos/Await/Follow 的竞争。
        maid.setAggressive(true);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);

        if (target instanceof ServerPlayer player) {
            setGameModeByCommand(player, GameType.SURVIVAL);
        }

        // 通知主人（不上 AI 对话）
        ServerPlayer owner = getOwnerAsPlayer(maid);
        if (owner != null) {
            owner.sendSystemMessage(
                    Component.literal("§c[狩猎] ")
                            .append(maid.getName())
                            .append(Component.literal(" 开始狩猎："))
                            .append(target.getName().copy())
                            .append(Component.literal("！"))
            );
        }
    }

    // ===== 维持狩猎（追击 + 攻击 + 交战对话） =====
    private void tickActiveHunt(ActiveHunt hunt, MinecraftServer server) {
        EntityMaid maid = hunt.maid;
        if (maid.isRemoved() || !maid.isAlive()) {
            endHunt(hunt);
            return;
        }

        UUID maidId = maid.getUUID();

        // 目标已被玩家从狩猎令移除 → 立即停止追猎恢复正常
        if (!HuntOrderData.contains(maid, hunt.targetId)) {
            endHunt(hunt);
            return;
        }

        Entity entity = resolveEntity(server, hunt.targetId);
        if (entity == null) {
            endHunt(hunt); // 实体不存在（可能未加载/下线），停止但不删条目
            return;
        }
        if (!(entity instanceof LivingEntity target)) {
            endHunt(hunt);
            return;
        }
        if (!target.isAlive()) {
            // 目标死亡 → 狩猎成功，自动从名单移除该目标
            // 玩家目标死亡时不再调回游戏模式（死了就死了，防止死亡界面被切模式卡住无法复活）
            hunt.targetDied = true;
            HuntOrderData.removeEntry(maid, hunt.targetId);
            endHunt(hunt);
            return;
        }
        // 目标离开女仆 15 格 → 停止狩猎，保留条目
        if (!entity.level().equals(maid.level()) || target.distanceToSqr(maid) > HUNT_RANGE_SQR) {
            endHunt(hunt);
            return;
        }

        // 旁观/创造等任何模式都不丢目标：只要不是生存就切生存（旁观会被切成生存继续索敌）
        if (target instanceof ServerPlayer player && player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            setGameModeByCommand(player, GameType.SURVIVAL);
        }

        // 起立 + 看向目标
        maid.setInSittingPose(false);
        maid.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target.blockPosition()));

        // 每秒重新检查一次背包：新拿到枪械时切枪械任务，有武器时切近战任务。
        if (maid.tickCount % SEARCH_TICKS == 0) {
            hunt.hasUsableWeapon = configureCombatLoadout(hunt);
        }

        maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        maid.setTarget(target);
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        if (hunt.hasUsableWeapon && hunt.manualMeleeWeapon && hunt.protectionBypassTarget
                && target.distanceToSqr(maid) <= MELEE_RANGE_SQR) {
            int cd = meleeCooldown.getOrDefault(maidId, 0);
            if (cd <= 0) {
                maid.swing(InteractionHand.MAIN_HAND);
                dealProtectedMeleeDamage(maid, target);
                meleeCooldown.put(maidId, MELEE_COOLDOWN_TICKS);
            } else {
                meleeCooldown.put(maidId, cd - 1);
            }
        } else if (hunt.hasUsableWeapon) {
            // 枪械及其他远程任务仍负责射击/换弹；命中后由强制伤害账本兑现原始伤害。
            meleeCooldown.put(maidId, 0);
        } else if (target.distanceToSqr(maid) <= MELEE_RANGE_SQR) {
            // 没有任何可用武器时才手动空手攻击，固定一秒一次。
            int cd = meleeCooldown.getOrDefault(maidId, 0);
            if (cd <= 0) {
                maid.swing(InteractionHand.MAIN_HAND);
                if (hunt.protectionBypassTarget) {
                    dealProtectedEmptyHandDamage(maid, target);
                } else {
                    maid.doHurtTarget(target);
                }
                meleeCooldown.put(maidId, MELEE_COOLDOWN_TICKS);
            } else {
                meleeCooldown.put(maidId, cd - 1);
            }
        } else {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(target.blockPosition()), 1.1f, 1));
            meleeCooldown.put(maidId, 0);
        }

        // 交战/追击阶段才说话，统一 30 秒 CD
        if (target.distanceToSqr(maid) <= CHASE_DIALOGUE_RANGE_SQR) {
            ServerPlayer owner = getOwnerAsPlayer(maid);
            if (owner != null) {
                String instruction = "你正在追击狩猎目标" + hunt.targetName + "。" + EmotionData.getTendencyPromptSuffix(maid, maid.getOwnerUUID())
                        + " 请用简短的话激励自己，或嘲讽猎物，展示你的决心。";
                tryDialogue(maid, owner, instruction);
            }
        }
    }

    // ===== 结束狩猎 =====
    private void endHunt(ActiveHunt hunt) {
        activeHunts.remove(hunt.maid.getUUID());
        meleeCooldown.remove(hunt.maid.getUUID());
        EntityMaid maid = hunt.maid;

        // 解除目标锁定：无论目标种类，离开狩猎后都立即恢复正常保护。
        if (hunt.protectionBypassTarget) {
            releaseHuntLock(maid.level(), hunt.lastTargetPos, hunt.targetId);
        }

        // 恢复玩家原游戏模式（/gamemode 命令）：
        if (hunt.targetPlayerUuid != null && !hunt.targetDied) {
            Entity e = resolveEntityForMaid(maid, hunt.targetPlayerUuid);
            if (e instanceof ServerPlayer player && hunt.prevGameMode != null) {
                setGameModeByCommand(player, hunt.prevGameMode);
            }
        }

        if (maid.isRemoved() || !maid.isAlive()) {
            MaidMovementControl.end(maid, MaidMovementControl.Reason.HUNT);
            return;
        }

        MaidMovementControl.clearNavigation(maid);
        MaidMovementControl.end(maid, MaidMovementControl.Reason.HUNT);
        maid.setAggressive(false);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    // ===== 彩蛋 1：目标是自己（对话受 30 秒 CD，10 秒后直接移除实体） =====
    private void triggerSelfSuicide(EntityMaid maid) {
        if (suicideTickets.containsKey(maid.getUUID())) {
            return;
        }
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.HUNT,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
        suicideTickets.put(maid.getUUID(), maid.level().getGameTime() + SUICIDE_WAIT_TICKS);
        maid.setInSittingPose(true);

        ServerPlayer owner = getOwnerAsPlayer(maid);
        if (owner != null) {
            String instruction = "主人现在想要你的命，需要你赴死。你顺从地坐下，心中异常平静——" + EmotionData.getTendencyPromptSuffix(maid, maid.getOwnerUUID())
                    + " 请用温柔而决绝的语气向主人道别，坦然接受死亡，然后安静等待。";
            tryDialogue(maid, owner, instruction);
        }
    }

    // ===== 统一对话（30 秒 CD） =====
    private static void tryDialogue(EntityMaid maid, ServerPlayer owner, String instruction) {
        long now = maid.level().getGameTime();
        Long last = lastDialogueTime.get(maid.getUUID());
        if (last != null && now - last < DIALOGUE_COOLDOWN_TICKS) {
            return;
        }
        lastDialogueTime.put(maid.getUUID(), now);
        MaidResponder.processBroadcast(owner, List.of(maid), instruction, false);
    }

    // ===== 战斗任务与装备选择 =====
    private static boolean configureCombatLoadout(ActiveHunt hunt) {
        EntityMaid maid = hunt.maid;

        // 优先使用 TLM 本体注册的通用枪械任务；任务不存在时自动跳过。
        IMaidTask gunTask = TaskManager.findTask(GUN_ATTACK_TASK_ID).orElse(null);
        if (gunTask instanceof IAttackTask gunAttack
                && equipWeaponToMainHand(maid, gunAttack)) {
            switchTask(hunt, gunTask);
            hunt.manualMeleeWeapon = false;
            return true;
        }

        IMaidTask attackTask = TaskManager.findTask(ATTACK_TASK_ID).orElse(null);
        if (attackTask instanceof IAttackTask meleeAttack
                && equipWeaponToMainHand(maid, meleeAttack)) {
            switchTask(hunt, attackTask);
            hunt.manualMeleeWeapon = true;
            return true;
        }

        // 保留弓、三叉戟或第三方 IAttackTask：仅在其当前武器确实可用时继续使用。
        if (maid.getTask() instanceof IAttackTask currentAttack
                && equipWeaponToMainHand(maid, currentAttack)) {
            hunt.manualMeleeWeapon = false;
            return true;
        }

        if (attackTask != null) {
            switchTask(hunt, attackTask);
        }
        TaskEquipUtil.putMainHandBack(maid);
        hunt.manualMeleeWeapon = false;
        return false;
    }


    private static boolean equipWeaponToMainHand(EntityMaid maid, IAttackTask attackTask) {
        if (attackTask.isWeapon(maid, maid.getMainHandItem())) {
            return true;
        }
        if (attackTask.isWeapon(maid, maid.getOffhandItem())) {
            var mainHand = maid.getMainHandItem();
            var offHand = maid.getOffhandItem();
            maid.setItemInHand(InteractionHand.MAIN_HAND, offHand);
            maid.setItemInHand(InteractionHand.OFF_HAND, mainHand);
            return true;
        }
        return TaskEquipUtil.tryEquipFromBackpack(maid, stack -> attackTask.isWeapon(maid, stack));
    }

    private static void switchTask(ActiveHunt hunt, IMaidTask task) {
        if (hunt.maid.getTask() != task) {
            hunt.maid.setTask(task);
            hunt.taskChanged = true;
        }
    }


    private static void dealProtectedMeleeDamage(EntityMaid maid, LivingEntity target) {
        float damage = (float) maid.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (!maid.getMainHandItem().isEmpty()) {
            damage += EnchantmentHelper.getDamageBonus(maid.getMainHandItem(), target.getMobType());
        }
        damage = Math.max(1.0f, damage);
        DamageSource source = target.damageSources().mobAttack(maid);
        target.invulnerableTime = 0;
        long queuedBefore = HuntDamageContext.forcedHitSequence();
        maid.doHurtTarget(target);
        if (HuntDamageContext.forcedHitSequence() == queuedBefore) {
            queueForcedHit(target, source, damage);
        }
    }

    private static void dealProtectedEmptyHandDamage(EntityMaid maid, LivingEntity target) {
        float damage = Math.max(1.0f, (float) maid.getAttributeValue(Attributes.ATTACK_DAMAGE));
        DamageSource source = target.damageSources().mobAttack(maid);
        target.invulnerableTime = 0;
        long queuedBefore = HuntDamageContext.forcedHitSequence();
        target.hurt(source, damage);
        if (HuntDamageContext.forcedHitSequence() == queuedBefore) {
            queueForcedHit(target, source, damage);
        }
    }


    private static void processForcedDamage(MinecraftServer server) {
        for (Map.Entry<UUID, ForcedDamage> entry : new ArrayList<>(pendingForcedDamage.entrySet())) {
            ForcedDamage pending = pendingForcedDamage.remove(entry.getKey());
            if (pending == null) continue;

            Entity entity = resolveEntity(server, entry.getKey());
            if (!(entity instanceof LivingEntity target) || target.isRemoved()
                    || !needsDirectHealthSettlement(target)) {
                HuntDamageContext.clearCaptured(entry.getKey());
                continue;
            }

            Entity attacker = resolveEntity(server, pending.attackerId);
            if (!(attacker instanceof EntityMaid maid) || !isHuntTarget(maid, target)) {
                HuntDamageContext.clearCaptured(entry.getKey());
                continue;
            }

            float desiredHealth = Math.max(0.0f, pending.healthBefore - pending.damage);
            HuntDamageContext.begin(target.getUUID(), pending.damage, pending.source);
            try {
                if (target.getHealth() > desiredHealth) {
                    HuntRawHealth.write(target, desiredHealth);
                }

                // 原生 hurt 被保护代码截断时，补发 1.20.1 的正式伤害包与原版击退。
                if (!pending.nativeHurtSucceeded) {
                    applyForcedHitFeedback(target, maid, pending.source);
                }

                if (desiredHealth > 0.0f && target.getHealth() > 0.0f) {
                    continue;
                }

                if (!needsForcedDeathSettlement(target)) {
                    target.die(pending.source);
                    continue;
                }

                for (ActiveHunt hunt : activeHunts.values()) {
                    if (hunt.targetId.equals(target.getUUID())) {
                        hunt.targetDied = true;
                        HuntOrderData.removeEntry(hunt.maid, hunt.targetId);
                    }
                }

                target.setAbsorptionAmount(0);
                HuntRawHealth.write(target, 0.0f);
                target.die(pending.source);
                // 死亡监听若尝试回血，返回后再次写零；死亡来源仍是本次枪械/武器的原始实例。
                HuntRawHealth.write(target, 0.0f);
                if (target instanceof EntityMaid maidTarget && !maidTarget.isRemoved()) {
                    maidTarget.remove(Entity.RemovalReason.KILLED);
                }
            } finally {
                HuntDamageContext.end();
                HuntDamageContext.clearCaptured(entry.getKey());
            }
        }
    }

    private static void applyForcedHitFeedback(LivingEntity target, Entity attacker, DamageSource source) {
        target.hurtTime = 10;
        target.hurtDuration = 10;
        target.hurtMarked = true;
        target.level().broadcastDamageEvent(target, source);

        // 与 LivingEntity.hurt 相同：爆炸由爆炸系统负责位移，直接攻击和枪弹由攻击者方向击退。
        if (attacker == null || source.is(DamageTypeTags.IS_EXPLOSION)) return;
        double ratioX = attacker.getX() - target.getX();
        double ratioZ = attacker.getZ() - target.getZ();
        while (ratioX * ratioX + ratioZ * ratioZ < 1.0E-4D) {
            ratioX = (Math.random() - Math.random()) * 0.01D;
            ratioZ = (Math.random() - Math.random()) * 0.01D;
        }

        var before = target.getDeltaMovement();
        target.knockback(0.4D, ratioX, ratioZ);
        // 某些整合包会取消 LivingKnockBackEvent；仅在完全没有产生位移时按原版公式兜底。
        if (target.getDeltaMovement().distanceToSqr(before) < 1.0E-8D) {
            var push = new net.minecraft.world.phys.Vec3(ratioX, 0.0D, ratioZ).normalize().scale(0.4D);
            target.setDeltaMovement(
                    before.x / 2.0D - push.x,
                    target.onGround() ? Math.min(0.4D, before.y / 2.0D + 0.4D) : before.y,
                    before.z / 2.0D - push.z);
        }
        target.hurtMarked = true;
    }

    // ===== /gamemode 命令切换（比 direct-gameMode 更彻底） =====
    private static void setGameModeByCommand(ServerPlayer player, GameType type) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String name = player.getGameProfile().getName();
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                "gamemode " + type.getName() + " \"" + name + "\"");
    }

    // ===== 工具 =====
    private static ServerPlayer getOwnerAsPlayer(EntityMaid maid) {
        if (maid.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }

    public static Entity resolveEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static Entity resolveEntityForMaid(EntityMaid maid, UUID uuid) {
        if (maid.getServer() == null) {
            return null;
        }
        return resolveEntity(maid.getServer(), uuid);
    }

    // ===== 女仆死亡清理 =====
    @SubscribeEvent
    public void onMaidDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        UUID maidId = maid.getUUID();
        ActiveHunt hunt = activeHunts.remove(maidId);
        if (hunt != null && hunt.protectionBypassTarget) {
            releaseHuntLock(maid.level(), hunt.lastTargetPos, hunt.targetId);
        }
        suicideTickets.remove(maidId);
        lastDialogueTime.remove(maidId);
        meleeCooldown.remove(maidId);
        HuntOrderData.removeSelf(maid);
        MaidMovementControl.end(maid, MaidMovementControl.Reason.HUNT);
    }
}
