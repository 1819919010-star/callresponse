package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 把广播动作的旧 Timer/Executor 改为服务器主线程 tick 待办。 */
public final class BroadcastMovementScheduler {
    private static final long WALK_TIMEOUT = 20L * 30L;
    private static final long ATTACK_TIMEOUT = 20L * 120L;
    private static final Map<UUID, WalkJob> WALKS = new HashMap<>();
    private static final Map<UUID, AttackJob> ATTACKS = new HashMap<>();

    public enum WalkKind { SIT, TAKE_FOOD }

    private record WalkJob(EntityMaid maid, UUID ownerId, UUID debugId, WalkKind kind, long until) {
    }

    private record AttackJob(EntityMaid maid, EntityMaid target, UUID debugId, long until, long nextAttack) {
        AttackJob next(long tick) {
            return new AttackJob(maid, target, debugId, until, tick);
        }
    }

    public static void startWalk(EntityMaid maid, LivingEntity owner, ServerPlayer debug, WalkKind kind) {
        stopWalk(maid, false);
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.BROADCAST_WALK,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE));
        maid.setInSittingPose(false);
        WALKS.put(maid.getUUID(), new WalkJob(maid, owner.getUUID(),
                debug == null ? null : debug.getUUID(), kind, maid.level().getGameTime() + WALK_TIMEOUT));
    }

    public static void startAttack(EntityMaid maid, EntityMaid target, ServerPlayer debug) {
        stopAttack(maid);
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.BROADCAST_ATTACK,
                java.util.EnumSet.of(MaidMovementControl.Field.PATH, MaidMovementControl.Field.POSE,
                        MaidMovementControl.Field.TASK));
        maid.setInSittingPose(false);
        long now = maid.level().getGameTime();
        ATTACKS.put(maid.getUUID(), new AttackJob(maid, target,
                debug == null ? null : debug.getUUID(), now + ATTACK_TIMEOUT, now));
    }

    public static boolean isAttacking(EntityMaid maid) {
        AttackJob job = ATTACKS.get(maid.getUUID());
        return job != null && job.maid == maid;
    }

    public static void stopAttack(EntityMaid maid) {
        AttackJob removed = ATTACKS.remove(maid.getUUID());
        if (removed == null && !MaidMovementControl.isActive(maid, MaidMovementControl.Reason.BROADCAST_ATTACK)) {
            return;
        }
        maid.setTarget(null);
        maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        MaidMovementControl.clearNavigation(maid);
        MaidMovementControl.end(maid, MaidMovementControl.Reason.BROADCAST_ATTACK);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long now = event.getServer().overworld().getGameTime();
        tickWalks(event, now);
        tickAttacks(now);
    }

    private static void tickWalks(TickEvent.ServerTickEvent event, long now) {
        for (WalkJob job : List.copyOf(WALKS.values())) {
            EntityMaid maid = job.maid;
            LivingEntity owner = null;
            for (var level : event.getServer().getAllLevels()) {
                if (level.getEntity(job.ownerId) instanceof LivingEntity found) {
                    owner = found;
                    break;
                }
            }
            if (!maid.isAlive() || maid.isRemoved() || owner == null || !owner.isAlive()
                    || owner.level() != maid.level() || now >= job.until) {
                stopWalk(maid, false);
                continue;
            }
            if (maid.distanceToSqr(owner) <= 2.25D) {
                WALKS.remove(maid.getUUID(), job);
                ServerPlayer debug = job.debugId == null ? null
                        : event.getServer().getPlayerList().getPlayer(job.debugId);
                MaidMovementControl.clearNavigation(maid);
                MaidMovementControl.end(maid, MaidMovementControl.Reason.BROADCAST_WALK);
                if (job.kind == WalkKind.SIT) {
                    WalkToOwnerAndSitAction.onArrived(maid, debug);
                } else {
                    WalkToOwnerAndTakeFoodAction.onArrived(maid, owner, debug);
                }
                continue;
            }
            maid.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new EntityTracker(owner, false), 0.7F, 1));
            maid.getNavigation().moveTo(owner, 0.7D);
        }
    }

    private static void tickAttacks(long now) {
        for (AttackJob job : List.copyOf(ATTACKS.values())) {
            EntityMaid maid = job.maid;
            EntityMaid target = job.target;
            if (!maid.isAlive() || maid.isRemoved() || !target.isAlive() || target.isRemoved()
                    || target.level() != maid.level() || now >= job.until) {
                stopAttack(maid);
                continue;
            }
            maid.setTarget(target);
            maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
            if (maid.distanceToSqr(target) > 4.0D) {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new EntityTracker(target, false), 0.8F, 1));
            } else if (now >= job.nextAttack) {
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                AttackOtherMaidAction.performMeleeAttack(maid, target);
                ATTACKS.put(maid.getUUID(), job.next(now + 10L));
            }
        }
    }

    private static void stopWalk(EntityMaid maid, boolean successfulSit) {
        WALKS.remove(maid.getUUID());
        MaidMovementControl.clearNavigation(maid);
        MaidMovementControl.end(maid, MaidMovementControl.Reason.BROADCAST_WALK);
        if (successfulSit) maid.setInSittingPose(true);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) cleanup(maid);
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide) cleanup(maid);
    }

    @SubscribeEvent
    public void onStopping(ServerStoppingEvent event) {
        for (WalkJob job : List.copyOf(WALKS.values())) stopWalk(job.maid, false);
        for (AttackJob job : List.copyOf(ATTACKS.values())) stopAttack(job.maid);
        WALKS.clear();
        ATTACKS.clear();
    }

    private static void cleanup(EntityMaid maid) {
        stopWalk(maid, false);
        stopAttack(maid);
    }
}
