package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 复仇女仆面对垫高玩家时使用的短期协作骑乘队形。 */
public final class BetrayalOutpostStackManager {
    private static final long TICK_INTERVAL = 2L;
    private static final long ASSIST_DURATION = 20L * 3L;
    private static final double NEAR_HEIGHT_TRIGGER = 2.0D;
    private static final double NEAR_HORIZONTAL_DISTANCE_SQR = 3.0D * 3.0D;
    private static final double HOME_HEIGHT_TRIGGER = 2.75D;
    private static final double ASSEMBLY_DISTANCE_SQR = 2.6D * 2.6D;
    private static final double MELEE_REACH_SQR = 2.25D * 2.25D;

    private static final Map<String, StackState> STATES = new HashMap<>();
    private static final Map<String, Long> NEXT_TICK = new HashMap<>();

    private BetrayalOutpostStackManager() {
    }

    public static void tick(EntityMaid caller) {
        if (!(caller.level() instanceof ServerLevel level) || !BetrayalOutpostMaidData.isOutpostMaid(caller)) {
            return;
        }
        String key = key(level, caller);
        long now = level.getGameTime();
        if (now < NEXT_TICK.getOrDefault(key, 0L)) return;
        NEXT_TICK.put(key, now + TICK_INTERVAL);

        List<EntityMaid> group = findGroup(level, caller);
        Formation formation = Formation.create(group);
        if (formation == null) {
            stop(key, group);
            return;
        }

        LivingEntity target = findElevatedTarget(group, formation, BetrayalOutpostMaidData.home(caller));
        if (target == null) {
            stop(key, group);
            return;
        }
        shareTarget(group, target);

        StackState state = STATES.computeIfAbsent(key, ignored -> new StackState(Phase.ASSIST_ASSEMBLING, now));
        if (state.phase() != Phase.TOWER) {
            boolean complete = applyAssistFormation(key, group, formation);
            if (!complete) {
                if (state.phase() != Phase.ASSIST_ASSEMBLING) {
                    STATES.put(key, new StackState(Phase.ASSIST_ASSEMBLING, now));
                }
                return;
            }

            if (state.phase() == Phase.ASSIST_ASSEMBLING) {
                STATES.put(key, new StackState(Phase.ASSIST_ACTIVE, now,
                        Set.of(formation.farmers().get(0).getUUID(), formation.farmers().get(1).getUUID())));
                return;
            }

            boolean combatCanReach = formation.combat().stream()
                    .anyMatch(maid -> maid.distanceToSqr(target) <= MELEE_REACH_SQR);
            if (!combatCanReach && now - state.startedAt() >= ASSIST_DURATION) {
                dismountSisters(group);
                STATES.put(key, new StackState(Phase.TOWER, now));
            }
            return;
        }

        applyTowerFormation(key, group, formation);
    }

    /** 队形活动时，除最底层移动者外，其余成员不再自行写入追击 WALK_TARGET。 */
    public static boolean isMovementDelegated(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || !BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            return false;
        }
        StackState state = STATES.get(key(level, maid));
        return state != null && !state.movers().contains(maid.getUUID());
    }

    /** The existing five-maid stack can substitute for a foot path only at close elevated targets. */
    public static boolean canBridgeElevation(EntityMaid maid, LivingEntity target) {
        if (!(maid.level() instanceof ServerLevel level)
                || !BetrayalOutpostMaidData.isWithinPursuitArea(maid, target)) return false;
        List<EntityMaid> group = findGroup(level, maid);
        return Formation.create(group) != null
                && target.getY() - group.stream().mapToDouble(Entity::getY).min().orElse(target.getY())
                >= NEAR_HEIGHT_TRIGGER
                && group.stream().anyMatch(member -> horizontalDistanceSqr(member, target)
                <= NEAR_HORIZONTAL_DISTANCE_SQR);
    }

    private static boolean applyAssistFormation(String key, List<EntityMaid> group, Formation formation) {
        EntityMaid farmer0 = formation.farmers().get(0);
        EntityMaid farmer1 = formation.farmers().get(1);
        EntityMaid sword0 = formation.swords().get(0);
        EntityMaid sword1 = formation.swords().get(1);
        EntityMaid heavy = formation.heavy();

        List<Link> links = List.of(new Link(sword0, farmer0), new Link(heavy, sword0), new Link(sword1, farmer1));
        if (matchesFormation(links)) {
            setMovers(key, Set.of(farmer0.getUUID(), farmer1.getUUID()));
            links.forEach(link -> stopRiderMovement(link.rider()));
            return true;
        }

        dismountSisters(group);
        setMovers(key, Set.of());
        stopAnchorMovement(farmer0);
        stopAnchorMovement(farmer1);
        boolean firstReady = gather(sword0, farmer0) & gather(heavy, farmer0);
        boolean secondReady = gather(sword1, farmer1);
        if (!firstReady || !secondReady) return false;

        // 人员都已在两个集结点旁边后再一次按序上人，避免底层移动导致永远骑不上。
        boolean mounted = sword0.startRiding(farmer0, true)
                && heavy.startRiding(sword0, true)
                && sword1.startRiding(farmer1, true);
        if (mounted) {
            links.forEach(link -> stopRiderMovement(link.rider()));
            setMovers(key, Set.of(farmer0.getUUID(), farmer1.getUUID()));
        }
        return mounted;
    }

    private static boolean applyTowerFormation(String key, List<EntityMaid> group, Formation formation) {
        EntityMaid bottom = formation.farmers().get(0);
        List<Link> links = List.of(
                new Link(formation.farmers().get(1), bottom),
                new Link(formation.swords().get(0), formation.farmers().get(1)),
                new Link(formation.swords().get(1), formation.swords().get(0)),
                new Link(formation.heavy(), formation.swords().get(1)));
        if (matchesFormation(links)) {
            setMovers(key, Set.of(bottom.getUUID()));
            links.forEach(link -> stopRiderMovement(link.rider()));
            return true;
        }

        // 先全部下来在最底层农务女仆旁集合，再从下到上一次完成整队骑乘。
        dismountSisters(group);
        setMovers(key, Set.of());
        stopAnchorMovement(bottom);
        boolean gathered = true;
        for (EntityMaid maid : group) {
            if (maid != bottom) gathered &= gather(maid, bottom);
        }
        if (!gathered) return false;

        boolean mounted = true;
        for (Link link : links) mounted &= link.rider().startRiding(link.vehicle(), true);
        if (mounted) {
            links.forEach(link -> stopRiderMovement(link.rider()));
            setMovers(key, Set.of(bottom.getUUID()));
        }
        return mounted;
    }

    private static boolean gather(EntityMaid maid, EntityMaid anchor) {
        if (maid.distanceToSqr(anchor) <= ASSEMBLY_DISTANCE_SQR) {
            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
            return true;
        }
        maid.getNavigation().moveTo(anchor, 1.0D);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new EntityTracker(anchor, false), 1.0F, 1));
        return false;
    }

    private static boolean matchesFormation(List<Link> links) {
        return links.stream().allMatch(link -> link.rider().getVehicle() == link.vehicle());
    }

    private static void setMovers(String key, Set<UUID> movers) {
        StackState old = STATES.get(key);
        STATES.put(key, new StackState(old == null ? Phase.ASSIST_ASSEMBLING : old.phase(),
                old == null ? 0L : old.startedAt(), Set.copyOf(movers)));
    }

    private static void stopAnchorMovement(EntityMaid anchor) {
        anchor.getNavigation().stop();
        anchor.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        anchor.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    private static void stopRiderMovement(EntityMaid rider) {
        rider.getNavigation().stop();
        rider.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        rider.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    private static void stop(String key, List<EntityMaid> group) {
        STATES.remove(key);
        // 骑乘关系会随实体保存；即使服务端刚重启、内存状态为空，也必须能安全拆队。
        dismountSisters(group);
    }

    private static void dismountSisters(List<EntityMaid> group) {
        for (EntityMaid maid : group) {
            if (maid.getVehicle() instanceof EntityMaid vehicle
                    && BetrayalOutpostMaidData.areSisters(maid, vehicle)) {
                maid.stopRiding();
            }
        }
    }

    private static List<EntityMaid> findGroup(ServerLevel level, EntityMaid maid) {
        AABB area = new AABB(BetrayalOutpostMaidData.home(maid)).inflate(18.0D, 32.0D, 18.0D);
        String group = BetrayalOutpostMaidData.group(maid);
        return level.getEntitiesOfClass(EntityMaid.class, area, candidate -> candidate.isAlive()
                && BetrayalOutpostMaidData.isOutpostMaid(candidate)
                && group.equals(BetrayalOutpostMaidData.group(candidate)));
    }

    private static LivingEntity findElevatedTarget(List<EntityMaid> group, Formation formation, BlockPos home) {
        LivingEntity best = null;
        double bestHorizontal = Double.MAX_VALUE;
        for (EntityMaid maid : group) {
            LivingEntity target = maid.getTarget();
            if (!isValidTarget(maid, target)
                    || !group.stream().allMatch(member -> isValidTarget(member, target))
                    || !shouldStack(group, target, home)) continue;
            double horizontal = horizontalDistanceSqr(formation.farmers().get(0), target);
            if (horizontal < bestHorizontal) {
                bestHorizontal = horizontal;
                best = target;
            }
        }
        return best;
    }

    private static boolean shouldStack(List<EntityMaid> group, LivingEntity target, BlockPos home) {
        double lowestY = group.stream().mapToDouble(Entity::getY).min().orElse(target.getY());
        double verticalGap = target.getY() - lowestY;
        double nearestHorizontal = group.stream()
                .mapToDouble(maid -> horizontalDistanceSqr(maid, target))
                .min().orElse(Double.MAX_VALUE);
        double homeGap = target.getY() - (home.getY() + 0.5D);
        // 目标明显高于营地时直接优先协作，不因个别女仆已经爬上房屋而取消。
        // 普通地形上则使用用户期望的近距离判断：水平3 格内且高度差至少 2 格。
        return homeGap >= HOME_HEIGHT_TRIGGER
                || (verticalGap >= NEAR_HEIGHT_TRIGGER && nearestHorizontal <= NEAR_HORIZONTAL_DISTANCE_SQR);
    }

    private static double horizontalDistanceSqr(Entity first, Entity second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean isValidTarget(EntityMaid maid, LivingEntity target) {
        if (target == null || !target.isAlive() || BetrayalOutpostMaidData.areSisters(maid, target)) return false;
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return BetrayalOutpostAlertManager.isBaseValidTarget(maid, target)
                && BetrayalOutpostAlertManager.canKeepOrDetectTarget(maid, target);
    }

    private static void shareTarget(List<EntityMaid> group, LivingEntity target) {
        for (EntityMaid maid : group) {
            BetrayalOutpostAlertManager.enterCombat(maid, target);
        }
    }

    private static String key(ServerLevel level, EntityMaid maid) {
        return level.dimension().location() + "|" + BetrayalOutpostMaidData.group(maid);
    }

    private enum Phase {
        ASSIST_ASSEMBLING,
        ASSIST_ACTIVE,
        TOWER
    }

    private record StackState(Phase phase, long startedAt, Set<UUID> movers) {
        private StackState(Phase phase, long startedAt) {
            this(phase, startedAt, Set.of());
        }
    }

    private record Link(EntityMaid rider, EntityMaid vehicle) {
    }

    private record Formation(List<EntityMaid> farmers, List<EntityMaid> swords, EntityMaid heavy) {
        private static Formation create(List<EntityMaid> group) {
            List<EntityMaid> farmers = new ArrayList<>();
            List<EntityMaid> swords = new ArrayList<>();
            EntityMaid heavy = null;
            for (EntityMaid maid : group) {
                switch (BetrayalOutpostMaidData.role(maid)) {
                    case FARMER, FEEDER -> farmers.add(maid);
                    case SWORDSMAN -> swords.add(maid);
                    case HEAVY -> heavy = maid;
                }
            }
            Comparator<EntityMaid> byUuid = Comparator.comparing(Entity::getStringUUID);
            farmers.sort(byUuid);
            swords.sort(byUuid);
            return farmers.size() == 2 && swords.size() == 2 && heavy != null
                    ? new Formation(farmers, swords, heavy) : null;
        }

        private List<EntityMaid> combat() {
            return List.of(swords.get(0), swords.get(1), heavy);
        }
    }
}
