package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.disguise.OutpostDisguiseRelations;
import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** 据点复仇女仆的休闲警戒与战斗中断，不改变她们的工作模式。 */
public final class BetrayalOutpostAlertManager {
    private static final String ENGAGED_TARGET = "callresponse:outpost_engaged_target";
    private static final double SNEAK_RADIUS_SQR = 3.0D * 3.0D;
    private static final long REACH_CACHE_TICKS = 20L * 2L;
    private static final Map<EntityMaid, Map<UUID, ReachCheck>> REACH_CACHE = new WeakHashMap<>();
    private static final Map<EntityMaid, PathBudget> PATH_BUDGET = new WeakHashMap<>();
    private static final Map<EntityMaid, SearchCheck> SEARCH_CACHE = new WeakHashMap<>();
    private static final Map<EntityMaid, Long> LEISURE_AFTER = new WeakHashMap<>();

    private record ReachCheck(BlockPos maidPos, BlockPos targetPos, long until, boolean reachable) {}
    private record PathBudget(long tick, int used) {}
    private record SearchCheck(long until, UUID target) {}

    private BetrayalOutpostAlertManager() {
    }

    public static void tick(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !BetrayalOutpostMaidData.isOutpostMaid(maid)) return;

        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                .orElse(maid.getTarget());
        if (target == null) {
            LivingEntity engaged = resolveEngagedTarget(level, maid);
            if (canKeepOrDetectTarget(maid, engaged)) {
                // Brain/TLM 若短暂清空 Mob target，仍恢复已经建立的战斗，避免潜行立刻脱战。
                enterCombat(maid, engaged);
                return;
            }
            clearEngagement(maid);
        }
        if (target != null) {
            if (!canKeepOrDetectTarget(maid, target)) {
                clearCombat(maid);
            } else {
                enterCombat(maid, target);
                return;
            }
        }

        if (!isRelaxed(maid)) return;
        LivingEntity detected = findRelaxedTarget(maid);
        if (detected != null) enterCombat(maid, detected);
    }

    /** 睡眠/娱乐之外不介入；已经建立的战斗目标不会因为对方再次潜行而丢失。 */
    public static boolean canKeepOrDetectTarget(EntityMaid maid, LivingEntity target) {
        if (!isBaseValidTarget(maid, target)) return false;
        if (!isEngagedTarget(maid, target) && isRelaxed(maid) && !canDetectWhileRelaxed(maid, target)) {
            return false;
        }
        return canReachEngagementPosition(maid, target);
    }

    public static LivingEntity findRelaxedTarget(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) return null;
        long now = level.getGameTime();
        SearchCheck cached = SEARCH_CACHE.get(maid);
        if (cached != null && now < cached.until()) {
            Entity entity = cached.target() == null ? null : level.getEntity(cached.target());
            return entity instanceof LivingEntity living && canKeepOrDetectTarget(maid, living) ? living : null;
        }
        AABB homeArea = BetrayalOutpostMaidData.pursuitSearchArea(maid);
        boolean relaxed = isRelaxed(maid);
        LivingEntity result = level.getEntitiesOfClass(LivingEntity.class, homeArea,
                        target -> isBaseValidTarget(maid, target))
                .stream().sorted(java.util.Comparator.<LivingEntity>comparingInt(
                        target -> !relaxed && target instanceof Player ? 0 : 1)
                        .thenComparingDouble(maid::distanceToSqr))
                .limit(6).filter(target -> canKeepOrDetectTarget(maid, target)).findFirst().orElse(null);
        // Share the scan between alert, combat selection and leisure checks; stagger whole groups.
        long next = now + 10 - Math.floorMod(now + maid.getId(), 10);
        SEARCH_CACHE.put(maid, new SearchCheck(next, result == null ? null : result.getUUID()));
        return result;
    }

    public static boolean isCombatRecovery(EntityMaid maid) {
        return maid.level().getGameTime() < LEISURE_AFTER.getOrDefault(maid, 0L);
    }

    /** Actual damage interrupts a seat even when the attacker is outside the permitted chase area. */
    public static void onHurt(EntityMaid maid, LivingEntity attacker) {
        if (maid.level().isClientSide || !BetrayalOutpostMaidData.isOutpostMaid(maid)
                || BetrayalOutpostMaidData.isGly(maid) || maid.isNoAi()
                || com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager.isIntimidated(maid)
                || (maid instanceof com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity revenge
                    && com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidBrain.externallyControlled(revenge))) return;
        LEISURE_AFTER.put(maid, maid.level().getGameTime() + 60);
        SEARCH_CACHE.remove(maid);
        interruptLeisure(maid);
        if (canKeepOrDetectTarget(maid, attacker)) enterCombat(maid, attacker);
    }

    private static void interruptLeisure(EntityMaid maid) {
        if (maid.isSleeping()) maid.stopSleeping();
        if (maid.getVehicle() instanceof EntitySit) maid.stopRiding();
        if (maid.isInSittingPose()) maid.setInSittingPose(false);
        if (hasLeisureTarget(maid, true) || hasLeisureWalkTarget(maid)) {
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
            maid.getNavigation().stop();
        }
    }

    /** 开始新的床/娱乐行为前检查整个现有营地追击范围。 */
    public static boolean shouldBlockLeisure(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !BetrayalOutpostMaidData.isOutpostMaid(maid)) return false;
        if (isCombatRecovery(maid)) return true;
        if (EmotionBetrayalManager.isReturningToOutpost(maid)) return true;
        if (canKeepOrDetectTarget(maid, maid.getTarget())) return true;
        return findRelaxedTarget(maid) != null;
    }

    public static boolean isRelaxed(EntityMaid maid) {
        if (maid.isSleeping() || maid.getVehicle() instanceof EntitySit) return true;
        return hasLeisureTarget(maid, false);
    }

    /** 归位需让出 TLM 的实际休息日程，包括尚未找到床或正在走向床的阶段。 */
    public static boolean isRestingOrSeekingRest(EntityMaid maid) {
        if (maid.isSleeping() || maid.getVehicle() instanceof EntitySit
                || hasLeisureTarget(maid, true) || hasLeisureWalkTarget(maid)) return true;
        var brain = maid.getBrain();
        Activity scheduled = brain.getSchedule().getActivityAt((int) (maid.level().getDayTime() % 24000L));
        return scheduled == Activity.REST || brain.isActive(Activity.REST)
                || brain.isActive(InitEntities.RIDE_REST.get());
    }

    /** 休闲态也使用现有营地追击区域；潜行规则仍在目标过滤处处理。 */
    public static AABB adjustSensorBox(EntityMaid maid, AABB original) {
        if (!BetrayalOutpostMaidData.isOutpostMaid(maid) || !isRelaxed(maid)
                || hasEngagedTarget(maid)) return original;
        return BetrayalOutpostMaidData.pursuitSearchArea(maid);
    }

    public static void enterCombat(EntityMaid maid, LivingEntity target) {
        if (!canKeepOrDetectTarget(maid, target)) return;
        LEISURE_AFTER.put(maid, maid.level().getGameTime() + 60);
        boolean newEngagement = !isEngagedTarget(maid, target);
        boolean interruptingLeisure = maid.isSleeping()
                || maid.getVehicle() instanceof EntitySit
                || maid.isInSittingPose()
                || hasLeisureTarget(maid, true);
        maid.getPersistentData().putUUID(ENGAGED_TARGET, target.getUUID());

        // 只在首次交战或仍被休闲状态占用时清理一次。不能每 tick 清空 WALK_TARGET，
        // 否则背叛攻击逻辑刚写入的追击路径会在下一实体 tick 被抹掉。
        if (newEngagement || interruptingLeisure) {
            if (maid.isSleeping()) maid.stopSleeping();
            if (maid.getVehicle() instanceof EntitySit) maid.stopRiding();
            if (maid.isInSittingPose()) maid.setInSittingPose(false);

            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            if (hasLeisureTarget(maid, true)) {
                maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            }
        }
        if (maid.getTarget() != target) maid.setTarget(target);
        if (maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) != target) {
            maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
        maid.setAggressive(true);
    }

    private static boolean canDetectWhileRelaxed(EntityMaid maid, LivingEntity target) {
        if (target instanceof Player player && player.isCrouching()) {
            return maid.distanceToSqr(player) <= SNEAK_RADIUS_SQR
                    && Math.abs(player.getY() - maid.getY()) <= 1.0D;
        }
        return true;
    }

    public static boolean isBaseValidTarget(EntityMaid maid, LivingEntity target) {
        if (BetrayalOutpostMaidData.isGly(maid)) return false;
        if (maid instanceof com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity
                && target != null && Math.abs(target.getY() - maid.getY()) > 6.0D) return false;
        return isCampCandidate(maid, target);
    }

    /** Snowballs are a GLY pastime, not a combat target or a reason to wake up. */
    public static LivingEntity findGlySnowballTarget(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !BetrayalOutpostMaidData.isGly(maid)) return null;
        return level.getEntitiesOfClass(LivingEntity.class, maid.getBoundingBox().inflate(16.0D),
                        target -> isCampCandidate(maid, target) && maid.distanceToSqr(target) <= 256.0D
                                && Math.abs(target.getY() - maid.getY()) <= 6.0D
                                && maid.hasLineOfSight(target))
                .stream().min(java.util.Comparator.comparingDouble(maid::distanceToSqr)).orElse(null);
    }

    private static boolean isCampCandidate(EntityMaid maid, LivingEntity target) {
        if (target == null || target == maid || !target.isAlive()) return false;
        if (!BetrayalOutpostMaidData.isMaidWithinPursuitArea(maid)) return false;
        // TLM TaskAttack drops targets beyond the maid's existing restriction radius.
        if (maid.distanceToSqr(target) > (double) BetrayalOutpostMaidData.CAMP_ACTIVITY_RADIUS
                * BetrayalOutpostMaidData.CAMP_ACTIVITY_RADIUS) return false;
        if (BetrayalOutpostMaidData.areSisters(maid, target)) return false;
        if (EmotionBetrayalManager.isTemporarilyUnreachable(maid, target)) return false;
        if (OutpostDisguiseRelations.isPacifiedTarget(maid, target)) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return BetrayalOutpostMaidData.isWithinPursuitArea(maid, target);
    }

    /** A reachable endpoint must be on the target's level and have a clear final attack lane. */
    private static boolean canReachEngagementPosition(EntityMaid maid, LivingEntity target) {
        // Joy seats are not transport/stack mounts: their passengers must be able to detect an enemy
        // BEFORE enterCombat dismounts them, otherwise sitting permanently vetoes combat.
        if (maid.isPassenger() && !(maid.getVehicle() instanceof EntitySit))
            return BetrayalOutpostStackManager.isMovementDelegated(maid);
        if (maid.isWithinMeleeAttackRange(target) && maid.getSensing().hasLineOfSight(target)) return true;
        if (!(maid.level() instanceof ServerLevel level)) return false;
        long now = level.getGameTime();
        Map<UUID, ReachCheck> byTarget = REACH_CACHE.computeIfAbsent(maid, ignored -> new HashMap<>());
        ReachCheck cached = byTarget.get(target.getUUID());
        BlockPos maidPos = maid.blockPosition();
        BlockPos targetPos = target.blockPosition();
        if (cached != null && now < cached.until() && cached.maidPos().distSqr(maidPos) <= 16.0D
                && cached.targetPos().distSqr(targetPos) <= 4.0D
                && cached.targetPos().getY() == targetPos.getY()) return cached.reachable();
        PathBudget budget = PATH_BUDGET.get(maid);
        boolean sameWindow = budget != null && now - budget.tick() < 10;
        int used = sameWindow ? budget.used() : 0;
        if (used >= 2) return cached != null && now < cached.until() && cached.reachable();
        PATH_BUDGET.put(maid, new PathBudget(sameWindow ? budget.tick() : now, used + 1));
        boolean reachable = hasCompletePathToAttackPosition(level, maid, target, targetPos)
                || BetrayalOutpostStackManager.canBridgeElevation(maid, target);
        if (byTarget.size() >= 8) byTarget.clear();
        byTarget.put(target.getUUID(), new ReachCheck(maidPos, targetPos, now + REACH_CACHE_TICKS, reachable));
        return reachable;
    }

    private static boolean hasCompletePathToAttackPosition(ServerLevel level, EntityMaid maid,
                                                            LivingEntity target, BlockPos targetPos) {
        BlockPos[] offsets = {BlockPos.ZERO, new BlockPos(0, 0, -1), new BlockPos(0, 0, 1),
                new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0)};
        java.util.Set<BlockPos> candidates = new java.util.HashSet<>();
        for (int dy : new int[]{0, 1, -1}) {
            for (BlockPos offset : offsets) {
                BlockPos candidate = targetPos.offset(offset.getX(), dy, offset.getZ());
                if (!safeAttackPosition(level, candidate) || !clearAttackLane(level, maid, target, candidate)) continue;
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) return false;
        Path path = maid.getNavigation().createPath(candidates, 0);
        Node end = path == null ? null : path.getEndNode();
        return path != null && path.canReach() && end != null
                && candidates.contains(new BlockPos(end.x, end.y, end.z));
    }

    private static boolean safeAttackPosition(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) return false;
        BlockPos floor = pos.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getFluidState(floor).isEmpty()
                && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty();
    }

    private static boolean clearAttackLane(ServerLevel level, EntityMaid maid,
                                           LivingEntity target, BlockPos pos) {
        Vec3 from = new Vec3(pos.getX() + 0.5D, pos.getY() + maid.getEyeHeight(), pos.getZ() + 0.5D);
        return from.distanceToSqr(target.getEyePosition()) <= 3.5D * 3.5D
                && level.clip(new ClipContext(from, target.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, maid)).getType() == HitResult.Type.MISS;
    }

    private static boolean hasLeisureTarget(EntityMaid maid, boolean includeBed) {
        return maid.getBrain().getMemory(InitEntities.TARGET_POS.get()).map(target -> {
            Block block = maid.level().getBlockState(target.currentBlockPosition()).getBlock();
            return block instanceof BlockJoy || (includeBed && block instanceof BlockMaidBed);
        }).orElse(false);
    }

    private static boolean hasLeisureWalkTarget(EntityMaid maid) {
        return maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(target -> {
            Block block = maid.level().getBlockState(target.getTarget().currentBlockPosition()).getBlock();
            return block instanceof BlockJoy || block instanceof BlockMaidBed;
        }).orElse(false);
    }

    private static boolean hasEngagedTarget(EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        return data.hasUUID(ENGAGED_TARGET);
    }

    private static LivingEntity resolveEngagedTarget(ServerLevel level, EntityMaid maid) {
        CompoundTag data = maid.getPersistentData();
        if (!data.hasUUID(ENGAGED_TARGET)) return null;
        Entity entity = level.getEntity(data.getUUID(ENGAGED_TARGET));
        return entity instanceof LivingEntity living ? living : null;
    }

    private static boolean isEngagedTarget(EntityMaid maid, LivingEntity target) {
        CompoundTag data = maid.getPersistentData();
        return data.hasUUID(ENGAGED_TARGET) && data.getUUID(ENGAGED_TARGET).equals(target.getUUID());
    }

    private static void clearEngagement(EntityMaid maid) {
        maid.getPersistentData().remove(ENGAGED_TARGET);
    }

    public static void clearCombat(EntityMaid maid) {
        boolean preserveLeisurePath = maid.isSleeping()
                || hasLeisureTarget(maid, true) || hasLeisureWalkTarget(maid);
        clearEngagement(maid);
        maid.setTarget(null);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (!preserveLeisurePath) {
            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        maid.setAggressive(false);
    }
}
