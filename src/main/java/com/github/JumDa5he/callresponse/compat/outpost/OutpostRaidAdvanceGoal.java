package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;

/** 只在营地特殊 Raid 中引导 Raider 从真正的围栏入口进入，不穿墙或传送。 */
public final class OutpostRaidAdvanceGoal extends Goal {
    private final Raider raider;
    private BlockPos chosenGate;
    private UUID checkedTarget;
    private int nextPathCheckTick;
    private boolean targetPathReachable;

    public OutpostRaidAdvanceGoal(Raider raider) {
        this.raider = raider;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return shouldAdvance();
    }

    @Override
    public boolean canContinueToUse() {
        return shouldAdvance();
    }

    @Override
    public void start() {
        moveTowardCamp();
    }

    @Override
    public void tick() {
        if (chosenGate != null && raider.level() instanceof ServerLevel level
                && raider.position().distanceToSqr(Vec3.atCenterOf(chosenGate)) <= 3.5D * 3.5D
                && openNearbyGates(level, chosenGate)) {
            nextPathCheckTick = 0;
            moveTowardCamp();
        } else if (raider.tickCount % 20 == 0 && raider.getNavigation().isDone()) {
            moveTowardCamp();
        }
    }

    @Override
    public void stop() {
        chosenGate = null;
        raider.getNavigation().stop();
    }

    private boolean shouldAdvance() {
        if (!OutpostMaidTargetGoal.isCampRaid(raider)) return false;
        LivingEntity target = raider.getTarget();
        if (target != null && !OutpostMaidTargetGoal.isCampTarget(raider, target)) return false;
        BlockPos center = raider.getCurrentRaid().getCenter();
        if (raider.level() instanceof ServerLevel level) {
            BetrayalOutpostSavedData.Outpost outpost = BetrayalOutpostSavedData.get(level).findAt(level, center);
            if (outpost != null && !insideFence(outpost.box())) return true;
            if (target != null) return !hasReachablePathTo(target);
        }
        double dx = raider.getX() - (center.getX() + 0.5D);
        double dz = raider.getZ() - (center.getZ() + 0.5D);
        return dx * dx + dz * dz > 6.0D * 6.0D;
    }

    private boolean insideFence(BoundingBox box) {
        return raider.getX() > box.minX() + 1 && raider.getX() < box.maxX() - 1
                && raider.getZ() > box.minZ() + 1 && raider.getZ() < box.maxZ() - 1;
    }

    private boolean hasReachablePathTo(LivingEntity target) {
        if (!target.getUUID().equals(checkedTarget) || raider.tickCount >= nextPathCheckTick) {
            checkedTarget = target.getUUID();
            nextPathCheckTick = raider.tickCount + 20;
            Path path = raider.getNavigation().createPath(target, 0);
            targetPathReachable = path != null && path.canReach();
        }
        return targetPathReachable;
    }

    private void moveTowardCamp() {
        BlockPos center = raider.getCurrentRaid().getCenter();
        if (raider.level() instanceof ServerLevel level) {
            LivingEntity target = raider.getTarget();
            BetrayalOutpostSavedData.Outpost outpost = BetrayalOutpostSavedData.get(level).findAt(level, center);
            if (OutpostMaidTargetGoal.isCampTarget(raider, target)) {
                Path path = raider.getNavigation().createPath(target, 0);
                if (path != null && path.canReach()) {
                    chosenGate = null;
                    raider.getNavigation().moveTo(path, 1.0D);
                    return;
                }
                if (outpost != null && moveToEntrance(level, outpost.box(), target.blockPosition())) return;
            } else {
                EntityMaid maid = level.getEntitiesOfClass(EntityMaid.class,
                                new AABB(center).inflate(24.0D, 16.0D, 24.0D),
                                candidate -> OutpostMaidTargetGoal.isCampTarget(raider, candidate))
                        .stream().min(Comparator.comparingDouble(raider::distanceToSqr)).orElse(null);
                Path path = maid == null ? null : raider.getNavigation().createPath(maid, 0);
                if (path != null && path.canReach()) {
                    chosenGate = null;
                    raider.getNavigation().moveTo(path, 1.0D);
                    return;
                }
                path = raider.getNavigation().createPath(center, 0);
                if (path != null && path.canReach()) {
                    chosenGate = null;
                    raider.getNavigation().moveTo(path, 1.0D);
                    return;
                }
                if (outpost != null && moveToEntrance(level, outpost.box(), center)) return;
            }
        }
        if (raider.getNavigation().moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, 1.0D)) return;
        Vec3 step = DefaultRandomPos.getPosTowards(raider, 12, 4, Vec3.atCenterOf(center), Math.PI / 2.0D);
        if (step != null) raider.getNavigation().moveTo(step.x, step.y, step.z, 1.0D);
    }

    private boolean moveToEntrance(ServerLevel level, BoundingBox box, BlockPos destination) {
        PathNavigation navigation = raider.getNavigation();
        boolean outsideCamp = !insideFence(box);
        Path bestPath = null;
        BlockPos bestGate = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos nearestGate = null;
        BlockPos nearestWaypoint = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int y = box.minY(); y <= Math.min(box.maxY(), box.minY() + 5); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos gate = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(gate);
                    if (!(state.getBlock() instanceof FenceGateBlock)) continue;
                    if (raider.position().distanceToSqr(Vec3.atCenterOf(gate)) <= 3.5D * 3.5D) {
                        openNearbyGates(level, gate);
                        state = level.getBlockState(gate);
                    }
                    Direction facing = state.getValue(FenceGateBlock.FACING);
                    for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
                        BlockPos nearSide = gate.relative(side, 2);
                        BlockPos farSide = gate.relative(side.getOpposite(), 2);
                        BlockPos waypoint = state.getValue(FenceGateBlock.OPEN) ? farSide : nearSide;
                        double distance = raider.distanceToSqr(Vec3.atCenterOf(nearSide));
                        if (distance < nearestDistance && (!outsideCamp || isBoundaryGate(box, gate))) {
                            nearestGate = gate;
                            nearestWaypoint = nearSide;
                            nearestDistance = distance;
                        }
                        Path path = navigation.createPath(waypoint, 0);
                        if (path == null || !path.canReach()) continue;
                        double score = path.getNodeCount() + Math.sqrt(farSide.distSqr(destination)) * 0.5D;
                        if (score >= bestScore) continue;
                        bestPath = path;
                        bestGate = gate;
                        bestScore = score;
                    }
                }
            }
        }
        if (bestPath == null) {
            if (nearestWaypoint == null) return false;
            chosenGate = nearestGate;
            Vec3 step = DefaultRandomPos.getPosTowards(raider, 12, 4,
                    Vec3.atCenterOf(nearestWaypoint), Math.PI / 2.0D);
            if (step != null && navigation.moveTo(step.x, step.y, step.z, 1.0D)) return true;
            return navigation.moveTo(nearestWaypoint.getX() + 0.5D,
                    nearestWaypoint.getY(), nearestWaypoint.getZ() + 0.5D, 1.0D);
        }
        chosenGate = bestGate;
        return navigation.moveTo(bestPath, 1.0D);
    }

    private static boolean isBoundaryGate(BoundingBox box, BlockPos gate) {
        return gate.getX() - box.minX() <= 2 || box.maxX() - gate.getX() <= 2
                || gate.getZ() - box.minZ() <= 2 || box.maxZ() - gate.getZ() <= 2;
    }

    private static boolean openNearbyGates(ServerLevel level, BlockPos gate) {
        boolean opened = false;
        for (BlockPos pos : BlockPos.betweenClosed(gate.offset(-1, 0, -1), gate.offset(1, 0, 1))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 3);
                opened = true;
            }
        }
        return opened;
    }
}
