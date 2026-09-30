package com.github.JumDa5he.callresponse.compat.outpost.entity;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.LinkedHashSet;

/** Small decisions for the existing Brain/MoveToTargetSink, not another navigation controller. */
final class RevengeMaidSafety {
    private long threatenedUntil, nextPathAt, nextHazardCheckAt, lastProgressAt, failedUntil;
    private WalkTarget ownedWalk;
    private boolean escaping;
    private boolean onHazard;
    private Vec3 progressPos;
    private BlockPos failedDestination;

    /** Current released 27x19x28 template: old anchor (16,1,16), doorstep (16,2,13).
     * Match its four rotated X/Z signatures, never infer a floor from a heightmap/current maid Y.
     * Keeping the original saved center avoids shifting combat/raid boundaries or changing old saves.
     */
    static BlockPos entrance(BoundingBox box, BlockPos home) {
        int x = home.getX() - box.minX(), z = home.getZ() - box.minZ();
        if (box.getXSpan() == 27 && box.getZSpan() == 28) {
            if (x == 16 && z == 16) return home.offset(0, 1, -3);
            if (x == 10 && z == 11) return home.offset(0, 1, 3);
        } else if (box.getXSpan() == 28 && box.getZSpan() == 27) {
            if (x == 11 && z == 16) return home.offset(3, 1, 0);
            if (x == 16 && z == 10) return home.offset(-3, 1, 0);
        }
        return home;
    }

    void onHurt(RevengeMaidEntity maid, DamageSource source) {
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.HOT_FLOOR) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)) {
            threatenedUntil = maid.level().getGameTime() + 80;
        }
    }

    boolean tick(RevengeMaidEntity maid) {
        long now = maid.level().getGameTime();
        if (now >= threatenedUntil) {
            // Spawn-on-fire, resistance/absorption and cancelled hurt callbacks must not prevent
            // escape. A cheap local contact check, independent of successfully losing health.
            if (now < nextHazardCheckAt) return false;
            nextHazardCheckAt = now + 10;
            if (!maid.isInLava() && !maid.isInWall() && !touchingHazard(maid)) {
                release(maid);
                return false;
            }
            threatenedUntil = now + 80;
        }
        boolean danger = maid.isInLava() || maid.isInWall() || touchingHazard(maid);
        onHazard = danger;
        if (!danger && (!escaping || ownedWalk == null
                || maid.position().distanceToSqr(ownedWalk.getTarget().currentPosition()) < 1)) {
            release(maid);
            return false;
        }
        // Respect cages/carry/intimidation through the entity's existing external-control gate.
        if (maid.isPassenger() && !(maid.getVehicle() instanceof EntitySit) || !maid.getPassengers().isEmpty()) {
            release(maid);
            return false;
        }
        if (maid.isSleeping()) maid.stopSleeping();
        if (maid.getVehicle() instanceof EntitySit) maid.stopRiding();
        maid.setInSittingPose(false);
        EmotionBetrayalManager.cancelOutpostReturn(maid);
        maid.getBrain().setActiveActivityIfPossible(Activity.PANIC);
        if (!escaping) {
            // Explicit handover from former work/combat; preserve its attack target for resumption.
            stopMoveSink(maid, now);
            maid.getBrain().eraseMemory(InitEntities.TARGET_POS.get());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
            maid.getNavigation().stop();
            escaping = true;
            progressPos = maid.position();
            lastProgressAt = now;
        }
        if (progressPos == null || maid.position().distanceToSqr(progressPos) >= 0.0625D) {
            progressPos = maid.position();
            lastProgressAt = now;
        }
        if (now < nextPathAt) return true;
        nextPathAt = now + 20;
        if (ownedWalk != null && now - lastProgressAt >= 40) {
            // A path object is not evidence that she moved. Retire a stuck destination briefly.
            failedDestination = ownedWalk.getTarget().currentBlockPosition();
            failedUntil = now + 60;
            releaseWalk(maid);
            lastProgressAt = now;
        }
        if (ownedWalk != null && !maid.getNavigation().isDone()
                && maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == ownedWalk
                && safeStanding(maid, ownedWalk.getTarget().currentBlockPosition())) return true;

        var candidates = new LinkedHashSet<BlockPos>();
        BlockPos origin = maid.blockPosition();
        // Cheap nearby block checks; ONE multi-target navigation query per retry, not one per block/tick.
        for (int radius = 1; radius <= 5 && candidates.size() < 48; radius++) {
            for (int dx = -radius; dx <= radius && candidates.size() < 48; dx++) {
                for (int dz = -radius; dz <= radius && candidates.size() < 48; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy : new int[]{0, 1, -1, 2, -2}) {
                        BlockPos pos = origin.offset(dx, dy, dz);
                        if (now < failedUntil && pos.equals(failedDestination)) continue;
                        if (maid.isWithinRestriction(pos) && safeStanding(maid, pos)) {
                            candidates.add(pos); break;
                        }
                    }
                }
            }
        }
        if (!candidates.isEmpty()) {
            Path path = maid.getNavigation().createPath(candidates, 0);
            if (path != null && path.canReach() && path.getEndNode() != null
                    && candidates.contains(path.getEndNode().asBlockPos())) {
                ownedWalk = new WalkTarget(path.getEndNode().asBlockPos(), 0.9F, 0);
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, ownedWalk);
            }
        }
        return true; // Retry if temporarily blocked; never teleport or enable domestic fallback AI.
    }

    void release(RevengeMaidEntity maid) {
        releaseWalk(maid);
        escaping = false;
        onHazard = false;
        progressPos = null;
        nextPathAt = 0;
    }

    float escapePathCost(net.minecraft.world.level.pathfinder.PathType type, float normal) {
        // WalkNodeEvaluator will not even step UP from a fire start when its overhead node
        // has negative malus. This traps egg spawns inside the campfire's half-slab ring.
        // Only while physically escaping a hazard allow that start with a high finite cost;
        // normal travel keeps its original forbidden fire/lava nodes.
        if (escaping && onHazard && normal < 0 && (type == net.minecraft.world.level.pathfinder.PathType.DAMAGE_FIRE
                || type == net.minecraft.world.level.pathfinder.PathType.DAMAGE_OTHER
                || type == net.minecraft.world.level.pathfinder.PathType.LAVA)) return 32;
        return normal;
    }

    private void releaseWalk(RevengeMaidEntity maid) {
        if (ownedWalk != null && maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == ownedWalk) {
            stopMoveSink(maid, maid.level().getGameTime());
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
            maid.getNavigation().stop();
        }
        ownedWalk = null;
    }

    private static void stopMoveSink(RevengeMaidEntity maid, long now) {
        // Stop only the existing movement executor during an explicit handover. Otherwise its
        // delayed stop would erase the new escape/fight WALK_TARGET on the following tick.
        for (var behavior : maid.getBrain().getRunningBehaviors()) {
            if (behavior instanceof net.minecraft.world.entity.ai.behavior.MoveToTargetSink)
                behavior.doStop((net.minecraft.server.level.ServerLevel) maid.level(), maid, now);
        }
    }

    static boolean safeStanding(RevengeMaidEntity maid, BlockPos pos) {
        if (!maid.level().hasChunkAt(pos) || hazardous(maid, pos.below())
                || !maid.level().getBlockState(pos.below()).isFaceSturdy(maid.level(), pos.below(), Direction.UP)) return false;
        var box = maid.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(maid.position()));
        if (!maid.level().noCollision(maid, box) || maid.level().containsAnyLiquid(box)) return false;
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (hazardous(maid, block)) return false;
        }
        return true;
    }

    private static boolean touchingHazard(RevengeMaidEntity maid) {
        var box = maid.getBoundingBox().inflate(0.05, 0.1, 0.05);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (harmfulBlock(maid, pos)) return true;
        }
        return false;
    }

    private static boolean hazardous(RevengeMaidEntity maid, BlockPos pos) {
        return !maid.level().getFluidState(pos).isEmpty() || harmfulBlock(maid, pos);
    }

    private static boolean harmfulBlock(RevengeMaidEntity maid, BlockPos pos) {
        var state = maid.level().getBlockState(pos);
        return maid.level().getFluidState(pos).is(net.minecraft.tags.FluidTags.LAVA) || state.is(BlockTags.FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }
}
