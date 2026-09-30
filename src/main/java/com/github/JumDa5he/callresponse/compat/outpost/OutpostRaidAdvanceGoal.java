package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
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
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Only idle members of a marked camp raid advance; normal combat owns movement after target acquisition. */
public final class OutpostRaidAdvanceGoal extends Goal {
    private static final int REPATH_TICKS = 20;
    private static final int FAILED_PATH_TICKS = 40;
    private final Raider raider;
    private BlockPos chosenGate;
    private BlockPos activeDestination;
    private BlockPos failedDestination;
    private BlockPos failedGate;
    private int failedDestinationUntilTick;
    private int failedGateUntilTick;
    private int nextPathTick;
    private int nextCheckTick;
    private int lastProgressTick;
    private Vec3 lastProgressPos;
    private Path ownedPath;

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
        lastProgressPos = raider.position();
        lastProgressTick = raider.tickCount;
        nextPathTick = raider.tickCount;
        moveTowardCamp();
    }

    @Override
    public void tick() {
        if (raider.tickCount < nextCheckTick || !(raider.level() instanceof ServerLevel level)) return;
        nextCheckTick = raider.tickCount + REPATH_TICKS;
        PathNavigation navigation = raider.getNavigation();
        if (chosenGate != null && raider.distanceToSqr(Vec3.atCenterOf(chosenGate)) <= 3.5D * 3.5D
                && openNearbyGates(level, chosenGate)) {
            stopOwnedPath();
            chosenGate = null;
            activeDestination = null;
            nextPathTick = raider.tickCount;
        }
        if (lastProgressPos == null || raider.position().distanceToSqr(lastProgressPos) >= 1.0D) {
            lastProgressPos = raider.position();
            lastProgressTick = raider.tickCount;
        } else if (ownedPath != null && raider.tickCount - lastProgressTick >= 80) {
            // Do not hammer the same blocked entrance; try another one after a short cooldown.
            failedDestination = activeDestination;
            failedDestinationUntilTick = raider.tickCount + 100;
            failedGate = chosenGate;
            failedGateUntilTick = raider.tickCount + 100;
            stopOwnedPath();
            chosenGate = null;
            activeDestination = null;
            nextPathTick = raider.tickCount + FAILED_PATH_TICKS;
        }
        if (raider.tickCount >= nextPathTick && (navigation.isDone() || navigation.getPath() != ownedPath)) {
            moveTowardCamp();
        }
    }

    @Override
    public void stop() {
        stopOwnedPath();
        chosenGate = null;
        activeDestination = null;
        lastProgressPos = null;
        nextPathTick = 0;
    }

    private void stopOwnedPath() {
        if (ownedPath != null && raider.getNavigation().getPath() == ownedPath) {
            raider.getNavigation().stop();
        }
        ownedPath = null;
    }

    private boolean shouldAdvance() {
        if (!OutpostMaidTargetGoal.isCampRaid(raider) || raider.isNoAi()
                || IntimidationManager.isIntimidated(raider)
                || OutpostMaidTargetGoal.hasValidCombatTarget(raider)
                || !(raider.level() instanceof ServerLevel level)) return false;
        BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(level)
                .findAt(level, raider.getCurrentRaid().getCenter());
        return camp != null && !insideCamp(camp);
    }

    private boolean insideCamp(BetrayalOutpostSavedData.Outpost camp) {
        BoundingBox box = camp.box();
        return raider.getX() > box.minX() + 1 && raider.getX() < box.maxX() - 1
                && raider.getZ() > box.minZ() + 1 && raider.getZ() < box.maxZ() - 1
                && raider.getY() >= camp.center().getY() - 3
                && raider.getY() <= camp.center().getY() + 8;
    }

    private void moveTowardCamp() {
        if (!(raider.level() instanceof ServerLevel level) || !shouldAdvance()) return;
        BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(level)
                .findAt(level, raider.getCurrentRaid().getCenter());
        if (camp == null) return;
        BlockPos center = camp.center();
        Set<BlockPos> destinations = new HashSet<>();
        for (int[] offset : new int[][]{{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                BlockPos destination = center.offset(offset[0], dy, offset[1]);
                if (!recentlyFailed(destination) && safeStand(level, destination)) destinations.add(destination);
            }
        }
        // One multi-target search, rather than up to 25 full path searches against the same building.
        Path centerPath = destinations.isEmpty() ? null : raider.getNavigation().createPath(destinations, 0);
        if (reachesAny(centerPath, destinations) && followPath(centerPath, null, endpoint(centerPath))) return;
        if (moveToEntrance(level, camp.box(), center)) return;
        Vec3 step = DefaultRandomPos.getPosTowards(raider, 12, 4, Vec3.atCenterOf(center), Math.PI / 2.0D);
        if (step != null && !recentlyFailed(BlockPos.containing(step))
                && safeStand(level, BlockPos.containing(step))
                && followPath(BlockPos.containing(step), null)) return;
        nextPathTick = raider.tickCount + FAILED_PATH_TICKS;
    }

    private boolean moveToEntrance(ServerLevel level, BoundingBox box, BlockPos center) {
        PathNavigation navigation = raider.getNavigation();
        Map<BlockPos, BlockPos> entrances = new HashMap<>();
        int minY = Math.max(box.minY(), center.getY() - 2);
        int maxY = Math.min(box.maxY(), center.getY() + 2);
        for (int y = minY; y <= maxY; y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    if (x - box.minX() > 2 && box.maxX() - x > 2
                            && z - box.minZ() > 2 && box.maxZ() - z > 2) continue;
                    BlockPos gate = new BlockPos(x, y, z);
                    if (!isBoundaryGate(box, gate) || gate.equals(failedGate) && raider.tickCount < failedGateUntilTick)
                        continue;
                    if (!level.hasChunkAt(gate)) continue;
                    BlockState state = level.getBlockState(gate);
                    if (!(state.getBlock() instanceof FenceGateBlock) || !gateFits(level, gate, state)) continue;
                    Direction facing = state.getValue(FenceGateBlock.FACING);
                    for (Direction side : new Direction[]{facing, facing.getOpposite()}) {
                        BlockPos waypoint = gate.relative(side, 2);
                        if (recentlyFailed(waypoint) || !safeStand(level, waypoint)) continue;
                        entrances.put(waypoint, gate);
                    }
                }
            }
        }
        Path path = entrances.isEmpty() ? null : navigation.createPath(entrances.keySet(), 0);
        if (!reachesAny(path, entrances.keySet())) return false;
        BlockPos destination = endpoint(path);
        return followPath(path, entrances.get(destination), destination);
    }

    private static BlockPos endpoint(Path path) {
        Node end = path.getEndNode();
        return new BlockPos(end.x, end.y, end.z);
    }

    private static boolean reachesAny(Path path, Set<BlockPos> destinations) {
        return path != null && path.canReach() && path.getEndNode() != null
                && destinations.contains(endpoint(path));
    }

    private boolean followPath(BlockPos destination, BlockPos gate) {
        Path path = raider.getNavigation().createPath(destination, 0);
        return reaches(path, destination) && followPath(path, gate, destination);
    }

    private boolean followPath(Path path, BlockPos gate, BlockPos destination) {
        if (!raider.getNavigation().moveTo(path, 1.0D)) return false;
        ownedPath = raider.getNavigation().getPath();
        chosenGate = gate;
        activeDestination = destination;
        nextPathTick = raider.tickCount + REPATH_TICKS;
        if (lastProgressPos == null || raider.position().distanceToSqr(lastProgressPos) >= 1.0D) {
            lastProgressPos = raider.position();
            lastProgressTick = raider.tickCount;
        }
        return true;
    }

    private boolean recentlyFailed(BlockPos destination) {
        return destination != null && destination.equals(failedDestination)
                && raider.tickCount < failedDestinationUntilTick;
    }

    private static boolean reaches(Path path, BlockPos destination) {
        if (path == null || !path.canReach()) return false;
        Node end = path.getEndNode();
        return end != null && Math.abs(end.x - destination.getX()) <= 1
                && Math.abs(end.y - destination.getY()) <= 1
                && Math.abs(end.z - destination.getZ()) <= 1;
    }

    private boolean safeStand(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty()
                || !level.getFluidState(pos.above()).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        AABB atPosition = raider.getBoundingBox().move(pos.getX() + 0.5D - raider.getX(),
                pos.getY() - raider.getY(), pos.getZ() + 0.5D - raider.getZ());
        return level.noCollision(raider, atPosition);
    }

    private boolean gateFits(ServerLevel level, BlockPos gate, BlockState state) {
        int needed = (int) Math.ceil(raider.getBbWidth());
        if (needed <= 1) return true;
        if (needed > 3) return false;
        Direction facing = state.getValue(FenceGateBlock.FACING);
        Direction lateral = facing.getClockWise();
        int width = 1;
        for (Direction side : new Direction[]{lateral, lateral.getOpposite()}) {
            for (int step = 1; step < needed; step++) {
                BlockState adjacent = level.getBlockState(gate.relative(side, step));
                if (!(adjacent.getBlock() instanceof FenceGateBlock)
                        || adjacent.getValue(FenceGateBlock.FACING).getAxis() != facing.getAxis()) break;
                width++;
            }
        }
        return width >= needed;
    }

    private static boolean isBoundaryGate(BoundingBox box, BlockPos gate) {
        return gate.getX() - box.minX() <= 2 || box.maxX() - gate.getX() <= 2
                || gate.getZ() - box.minZ() <= 2 || box.maxZ() - gate.getZ() <= 2;
    }

    private boolean openNearbyGates(ServerLevel level, BlockPos gate) {
        BlockState gateState = level.getBlockState(gate);
        if (!(gateState.getBlock() instanceof FenceGateBlock) || !gateFits(level, gate, gateState)) return false;
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
