package com.github.JumDa5he.callresponse.compat.outpost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/** 只在营地特殊 Raid 中引导 Raider 从真正的围栏入口进入，不穿墙或传送。 */
public final class OutpostRaidAdvanceGoal extends Goal {
    private final Raider raider;
    private BlockPos chosenGate;
    private BlockPos chosenDestination;
    private net.minecraft.world.entity.raid.Raid raidContext;
    private int nextPathCheckTick;
    private int retryAfterTick;
    private final Map<BlockPos, Integer> failedEntrances = new HashMap<>();
    private Path ownedPath;
    private Vec3 lastProgress;
    private int lastProgressTick;

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
        if (!shouldAdvance()) return;
        if (lastProgress == null || raider.position().distanceToSqr(lastProgress) >= 1.0D) {
            lastProgress = raider.position();
            lastProgressTick = raider.tickCount;
        } else if (raider.tickCount - lastProgressTick >= 60) {
            failPath();
            return;
        }
        if (chosenGate != null && raider.level() instanceof ServerLevel level
                && raider.position().distanceToSqr(Vec3.atCenterOf(chosenGate)) <= 3.5D * 3.5D
                && openNearbyGates(level, chosenGate)) {
            nextPathCheckTick = 0;
            moveTowardCamp();
        } else if (raider.tickCount >= nextPathCheckTick && raider.getNavigation().isDone()) {
            moveTowardCamp();
        }
    }

    @Override
    public void stop() {
        chosenGate = null;
        chosenDestination = null;
        if (ownedPath != null && raider.getNavigation().getPath() == ownedPath) raider.getNavigation().stop();
        ownedPath = null;
        lastProgress = null;
        if (!OutpostMaidTargetGoal.isCampRaid(raider)) {
            failedEntrances.clear();
            retryAfterTick = 0;
            nextPathCheckTick = 0;
        }
    }

    private boolean shouldAdvance() {
        if (raidContext != raider.getCurrentRaid()) {
            stop();
            failedEntrances.clear();
            retryAfterTick = 0;
            nextPathCheckTick = 0;
            raidContext = raider.getCurrentRaid();
        }
        if (!OutpostMaidTargetGoal.isCampRaid(raider)) {
            failedEntrances.clear();
            retryAfterTick = 0;
            return false;
        }
        if (!OutpostMaidTargetGoal.canAct(raider) || OutpostMaidTargetGoal.hasValidTarget(raider)
                || raider.tickCount < retryAfterTick) return false;
        BlockPos center = raider.getCurrentRaid().getCenter();
        if (raider.level() instanceof ServerLevel level) {
            BetrayalOutpostSavedData.Outpost outpost = BetrayalOutpostSavedData.get(level).findAt(level, center);
            if (outpost != null) return !insideFence(outpost.box())
                    || Math.abs(raider.getY() - outpost.center().getY()) > 3.0D;
        }
        return false;
    }

    private boolean insideFence(BoundingBox box) {
        return raider.getX() > box.minX() + 1 && raider.getX() < box.maxX() - 1
                && raider.getZ() > box.minZ() + 1 && raider.getZ() < box.maxZ() - 1;
    }

    private void moveTowardCamp() {
        if (!shouldAdvance() || raider.tickCount < nextPathCheckTick) return;
        nextPathCheckTick = raider.tickCount + 20;
        failedEntrances.entrySet().removeIf(entry -> entry.getValue() <= raider.tickCount);
        if (raider.level() instanceof ServerLevel level) {
            var outpost = BetrayalOutpostSavedData.get(level).findAt(level, raider.getCurrentRaid().getCenter());
            if (outpost != null) {
                if (moveToEntrance(level, outpost.box(), outpost.center())) {
                    rememberPath();
                    return;
                }
                // 只在营地记录的地面高度附近找落脚点，不能误用水底/平台下表面。
                for (int radius = 0; radius <= 6; radius += 2) {
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos horizontal = outpost.center().relative(direction, radius);
                        for (int dy = 3; dy >= -2; dy--) {
                            BlockPos ground = horizontal.above(dy);
                            if (failedEntrances.containsKey(ground) || !canStandAt(level, ground)) continue;
                            Path path = raider.getNavigation().createPath(ground, 0);
                            if (path != null && path.canReach() && raider.getNavigation().moveTo(path, 1.0D)) {
                                chosenGate = null;
                                chosenDestination = ground;
                                rememberPath();
                                return;
                            }
                        }
                    }
                }
            }
        }
        failPath();
    }

    private void rememberPath() {
        ownedPath = raider.getNavigation().getPath();
        if (lastProgress == null) {
            lastProgress = raider.position();
            lastProgressTick = raider.tickCount;
        }
    }

    private void failPath() {
        if (chosenGate != null) failedEntrances.put(chosenGate.immutable(), raider.tickCount + 200);
        if (chosenDestination != null) failedEntrances.put(chosenDestination.immutable(), raider.tickCount + 200);
        if (ownedPath != null && raider.getNavigation().getPath() == ownedPath) raider.getNavigation().stop();
        ownedPath = null;
        chosenGate = null;
        chosenDestination = null;
        lastProgress = null;
        retryAfterTick = raider.tickCount + 40;
    }

    private boolean canStandAt(ServerLevel level, BlockPos pos) {
        var body = raider.getBoundingBox().move(pos.getX() + 0.5D - raider.getX(),
                pos.getY() - raider.getY(), pos.getZ() + 0.5D - raider.getZ());
        for (BlockPos check : BlockPos.betweenClosed(BlockPos.containing(body.minX, body.minY - 1, body.minZ),
                BlockPos.containing(body.maxX, body.maxY, body.maxZ))) {
            if (!level.hasChunkAt(check)) return false;
        }
        return level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && level.noCollision(raider, body) && !level.containsAnyLiquid(body);
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
        for (int y = Math.max(box.minY(), destination.getY() - 2); y <= Math.min(box.maxY(), destination.getY() + 3); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos gate = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(gate) || failedEntrances.containsKey(gate)) continue;
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
                        if (!canStandAt(level, waypoint) || !canStandAt(level, nearSide)) continue;
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
            if (step != null && canStandAt(level, BlockPos.containing(step))) {
                Path segment = navigation.createPath(BlockPos.containing(step), 0);
                if (segment != null && segment.canReach() && navigation.moveTo(segment, 1.0D)) return true;
            }
            failedEntrances.put(nearestGate.immutable(), raider.tickCount + 200);
            return false;
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
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 3);
                opened = true;
            }
        }
        return opened;
    }
}
