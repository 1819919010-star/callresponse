package com.github.JumDa5he.callresponse.compat.outpost.entity;

import com.github.JumDa5he.callresponse.compat.facility.FacilityCapacityManager;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import java.util.*;

/** Camp-bed search uses real reachable beds, not the domestic sleep-radius/teleport system. */
final class RevengeMaidRest extends Behavior<EntityMaid> {
    private long nextSearch;
    private BlockPos bed;
    private WalkTarget issuedWalk;
    private final boolean sleeping;
    private final Map<BlockPos, Long> retryAfter = new HashMap<>();
    private long progressAt;
    private net.minecraft.world.phys.Vec3 progressPos;
    RevengeMaidRest() { this(true); }
    RevengeMaidRest(boolean sleeping) { super(Map.of()); this.sleeping = sleeping; }

    private Activity activity() { return sleeping ? Activity.REST : Activity.IDLE; }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return maid.getBrain().isActive(activity())
                && !com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostAlertManager.shouldBlockLeisure(maid)
                && !maid.isSleeping() && maid.canBrainMoving() && !maid.isUsingItem() && level.getGameTime() >= nextSearch;
    }
    @Override protected boolean canStillUse(ServerLevel level, EntityMaid maid, long now) {
        return maid.getBrain().isActive(activity()) && !maid.isSleeping() && maid.canBrainMoving();
    }
    @Override protected boolean timedOut(long now) { return false; }
    @Override protected void tick(ServerLevel level, EntityMaid maid, long now) {
        if (!maid.isUsingItem() && now >= nextSearch) start(level, maid, now);
    }
    @Override protected void stop(ServerLevel level, EntityMaid maid, long now) {
        release(maid);
        bed = null;
    }
    @Override protected void start(ServerLevel level, EntityMaid maid, long now) {
        nextSearch = now + 40;
        retryAfter.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (bed != null && !available(level, maid, bed)) { release(maid); bed = null; }
        if (bed != null && progressPos != null) {
            if (maid.position().distanceToSqr(progressPos) >= 1) {
                progressPos = maid.position(); progressAt = now;
            } else if (now - progressAt >= 100) {
                retryAfter.put(bed, now + 100); release(maid); bed = null;
            }
        }
        boolean checkedPath = false;
        if (bed == null) {
            var candidates = sleeping ? FacilityCapacityManager.collectBedCandidates(level, maid).positions().stream()
                    : level.getPoiManager().getInRange(p -> p.get().equals(InitPoi.JOY_BLOCK.get()),
                        maid.getBrainSearchPos(), (int) maid.getRestrictRadius(), PoiManager.Occupancy.ANY).map(PoiRecord::getPos);
            var beds = candidates.filter(p -> !retryAfter.containsKey(p) && available(level, maid, p))
                    .sorted(Comparator.comparingDouble(p -> p.distSqr(maid.blockPosition()))).limit(4).toList();
            for (BlockPos candidate : beds) {
                if (reachable(maid, candidate)) {
                    bed = candidate.immutable(); checkedPath = true;
                    progressPos = maid.position(); progressAt = now; break;
                }
                retryAfter.put(candidate.immutable(), now + 100);
            }
        }
        // No domestic Brain fallback: wait at the current legal camp point, then retry another candidate.
        if (bed == null) { release(maid); nextSearch = now + 100; return; }
        if (bed.distToCenterSqr(maid.position()) < 4 && Math.abs(maid.getY() - bed.getY()) <= 1.1) {
            release(maid);
            if (!sleeping) {
                var state = level.getBlockState(bed);
                ((BlockJoy) state.getBlock()).startMaidSit(maid, state, level, bed);
                if (!maid.isPassenger()) FacilityCapacityManager.trySeatExtraJoy(level, bed, maid);
            } else if (level.getBlockState(bed).getValue(BedBlock.OCCUPIED)) {
                FacilityCapacityManager.tryUseOccupiedBed(level, maid, bed);
            } else {
                maid.startSleeping(bed);
                maid.setPos(bed.getX() + 0.5, bed.getY() + 0.8, bed.getZ() + 0.5);
            }
            bed = null;
        } else if (checkedPath || reachable(maid, bed)) {
            issuedWalk = new WalkTarget(bed, 0.6F, 1);
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, issuedWalk);
        } else { retryAfter.put(bed, now + 100); release(maid); bed = null; }
    }

    private boolean available(ServerLevel level, EntityMaid maid, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !maid.isWithinRestriction(pos)) return false;
        var state = level.getBlockState(pos);
        if (!sleeping) return state.getBlock() instanceof BlockJoy && FacilityCapacityManager.hasVacancy(level, pos);
        return state.getBlock() instanceof BlockMaidBed && state.getValue(BlockMaidBed.PART) == BedPart.HEAD
                && FacilityCapacityManager.hasVacancy(level, pos)
                && (!state.getValue(BedBlock.OCCUPIED) || FacilityCapacityManager.hasCapacityOverride(level, pos));
    }
    private static boolean reachable(EntityMaid maid, BlockPos pos) {
        var path = maid.getNavigation().createPath(pos, 1);
        var end = path == null ? null : path.getEndNode();
        return path != null && path.canReach() && end != null && Math.abs(end.y - pos.getY()) <= 1
                && pos.distSqr(new BlockPos(end.x, end.y, end.z)) <= 4;
    }
    private void release(EntityMaid maid) {
        if (issuedWalk != null && maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == issuedWalk) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getNavigation().stop();
        }
        issuedWalk = null;
    }
}
