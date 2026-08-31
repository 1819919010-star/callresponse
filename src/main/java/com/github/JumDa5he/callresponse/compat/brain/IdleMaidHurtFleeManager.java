package com.github.JumDa5he.callresponse.compat.brain;

import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 空闲工作模式受伤后，临时接管路径并远离攻击者。 */
public final class IdleMaidHurtFleeManager {
    private static final double SAFE_DISTANCE = 10.0;
    private static final double SAFE_DISTANCE_SQR = SAFE_DISTANCE * SAFE_DISTANCE;
    private static final float WALK_SPEED = 0.7F;
    private static final int SEARCH_HORIZONTAL = 16;
    private static final int SEARCH_VERTICAL = 7;
    private static final int TEMP_RESTRICT_RADIUS = 32;
    private static final long TIMEOUT_TICKS = 20L * 15L;
    private static final long REPATH_INTERVAL = 10L;

    private static final Map<UUID, FleeState> FLEEING = new HashMap<>();

    private static final class FleeState {
        private final EntityMaid maid;
        private UUID attackerId;
        private Vec3 threatPos;
        private BlockPos target;
        private long startTick;
        private long nextRepathTick;

        private FleeState(EntityMaid maid, Entity attacker, long gameTime) {
            this.maid = maid;
            reset(attacker, gameTime);
        }

        private void reset(Entity attacker, long gameTime) {
            this.attackerId = attacker.getUUID();
            this.threatPos = attacker.position();
            this.target = null;
            this.startTick = gameTime;
            this.nextRepathTick = gameTime;
        }
    }

    @SubscribeEvent
    public void onMaidDamaged(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide()
                || !maid.isAlive() || event.getNewDamage() <= 0 || !isIdleTask(maid)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == maid || !attacker.isAlive()
                || attacker.level() != maid.level()) {
            return;
        }

        // 谈话、讨食、狩猎等更明确的附属流程已经接管时，不从中途抢走路径。
        if (MaidMovementControl.controlsOther(maid, MaidMovementControl.Reason.IDLE_HURT_FLEE,
                MaidMovementControl.Field.PATH)) {
            return;
        }

        long gameTime = maid.level().getGameTime();
        FleeState state = FLEEING.get(maid.getUUID());
        if (state == null) {
            MaidMovementControl.begin(maid, MaidMovementControl.Reason.IDLE_HURT_FLEE,
                    EnumSet.of(MaidMovementControl.Field.PATH,
                            MaidMovementControl.Field.SCHEDULE,
                            MaidMovementControl.Field.POSE));
            state = new FleeState(maid, attacker, gameTime);
            FLEEING.put(maid.getUUID(), state);
        } else {
            state.reset(attacker, gameTime);
        }

        // 对齐本体 MaidRunAwayTask：危险时先站起，再交给 WALK_TARGET 寻路。
        if (maid.isInSittingPose()) {
            maid.setInSittingPose(false);
        }
        maid.setHomeModeEnable(false);
        updateThreatAndPath(state, gameTime, true);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (FLEEING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, FleeState>> iterator = FLEEING.entrySet().iterator();
        while (iterator.hasNext()) {
            FleeState state = iterator.next().getValue();
            EntityMaid maid = state.maid;
            long gameTime = maid.level().getGameTime();

            if (!maid.isAlive() || maid.isRemoved() || !(maid.level() instanceof ServerLevel)
                    || !isIdleTask(maid) || gameTime - state.startTick >= TIMEOUT_TICKS
                    || MaidMovementControl.controlsOther(maid, MaidMovementControl.Reason.IDLE_HURT_FLEE,
                    MaidMovementControl.Field.PATH)) {
                finish(state, iterator);
                continue;
            }
            if (gameTime >= state.nextRepathTick) {
                updateThreatPosition(state);
                updateThreatAndPath(state, gameTime, false);
            }
        }
    }

    private static void updateThreatAndPath(FleeState state, long gameTime, boolean force) {
        EntityMaid maid = state.maid;
        if (!force && gameTime < state.nextRepathTick) {
            return;
        }
        if (!(maid.level() instanceof ServerLevel level)) {
            return;
        }
        state.nextRepathTick = gameTime + REPATH_INTERVAL;
        if (maid.distanceToSqr(state.threatPos) >= SAFE_DISTANCE_SQR) {
            return;
        }
        BlockPos target = findAwayTarget(maid, state.threatPos);
        if (target != null) {
            state.target = target;
            maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(target), WALK_SPEED, 1));
            maid.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, WALK_SPEED);
        }
    }

    /** 复用本体远离苦力怕任务采用的 LandRandomPos，并固定为本功能需要的搜索范围。 */
    private static BlockPos findAwayTarget(EntityMaid maid, Vec3 threatPos) {
        Vec3 best = null;
        double bestDistance = maid.position().distanceToSqr(threatPos);
        for (int i = 0; i < 10; i++) {
            Vec3 candidate = LandRandomPos.getPosAway(maid, SEARCH_HORIZONTAL, SEARCH_VERTICAL, threatPos);
            if (candidate == null) {
                continue;
            }
            double candidateDistance = candidate.distanceToSqr(threatPos);
            if (candidateDistance > bestDistance) {
                best = candidate;
                bestDistance = candidateDistance;
            }
            if (candidateDistance >= SAFE_DISTANCE_SQR) {
                break;
            }
        }
        if (best != null) {
            return BlockPos.containing(best);
        }

        Vec3 away = maid.position().subtract(threatPos);
        if (away.horizontalDistanceSqr() < 1.0E-4) {
            double angle = maid.getRandom().nextDouble() * Math.PI * 2.0;
            away = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        }
        Vec3 fallback = maid.position().add(away.normalize().scale(SAFE_DISTANCE + 2.0));
        return BlockPos.containing(fallback);
    }

    private static void updateThreatPosition(FleeState state) {
        if (!(state.maid.level() instanceof ServerLevel level)) {
            return;
        }
        Entity attacker = level.getEntity(state.attackerId);
        if (attacker != null && attacker.isAlive()) {
            state.threatPos = attacker.position();
        }
    }

    private static boolean isIdleTask(EntityMaid maid) {
        return maid.getTask() == TaskManager.getIdleTask()
                || maid.getTask().getUid().equals(TaskManager.getIdleTask().getUid());
    }

    private static void finish(FleeState state, Iterator<Map.Entry<UUID, FleeState>> iterator) {
        EntityMaid maid = state.maid;
        boolean owned = MaidMovementControl.isActive(maid, MaidMovementControl.Reason.IDLE_HURT_FLEE);
        if (owned) {
            MaidMovementControl.end(maid, MaidMovementControl.Reason.IDLE_HURT_FLEE);
        }
        if (owned && !MaidMovementControl.controlsPath(maid)) {
            MaidMovementControl.clearNavigation(maid);
        }
        iterator.remove();
    }

    private static void remove(EntityMaid maid) {
        FleeState state = FLEEING.remove(maid.getUUID());
        if (state != null && MaidMovementControl.isActive(maid,
                MaidMovementControl.Reason.IDLE_HURT_FLEE)) {
            MaidMovementControl.end(maid, MaidMovementControl.Reason.IDLE_HURT_FLEE);
        }
    }

    @SubscribeEvent
    public void onMaidDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            remove(maid);
        }
    }

    @SubscribeEvent
    public void onMaidLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            FLEEING.remove(maid.getUUID());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        FLEEING.clear();
    }
}
