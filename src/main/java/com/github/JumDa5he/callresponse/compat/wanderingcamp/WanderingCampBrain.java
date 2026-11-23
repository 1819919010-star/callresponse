package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One temporary Brain decision chain for ordinary EntityMaid; no domestic AI fallback. */
public final class WanderingCampBrain {
    private static final Map<EntityMaid, Control> CONTROLS = Collections.synchronizedMap(new WeakHashMap<>());
    private WanderingCampBrain() {}
    private static final class Control {
        WalkTarget walk;
        String mode = "";
        long nextMove, sitUntil, nextPickup, lastTick = -1, sleepDay = Long.MIN_VALUE;
        int attempts;
        boolean sleepEntered, sleepAbandoned;
    }

    public static void register(Brain<EntityMaid> brain) {
        brain.addActivity(Activity.CORE, ImmutableList.of(
                Pair.of(0, new MaidSwimJumpTask(.8F)), Pair.of(0, new MaidBreathAirTask()),
                Pair.of(0, new MaidBreathAirStopTask()), Pair.of(0, new MaidClimbTask()),
                Pair.of(2, new LookAtTargetSink(45, 90)), Pair.of(2, MaidInteractWithDoor.create()),
                Pair.of(3, new MoveToTargetSink()), Pair.of(4, new MaidHealSelfTask()),
                CampRoutineBehavior.EXTENSION.getCoreBehaviors().get(0)));
        brain.addActivity(Activity.IDLE, ImmutableList.of());
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
    }

    public static boolean blocked(EntityMaid maid) {
        return maid.guiOpening || maid.isNoAi() || IntimidationManager.isIntimidated(maid) || MaidMovementControl.controlsPath(maid)
                || MaidMovementControl.controlsPose(maid) || PrincessCarryManager.isMaidCarrySession(maid)
                || maid.isPassenger() || maid.isLeashed()
                || (!maid.canBrainMoving() && !WanderingCampData.data(maid).getBoolean("CampSit")
                    && !WanderingCampData.sleepingOnBedding(maid));
    }

    public static void tick(EntityMaid maid) {
        if (!CampPolicy.runsRoutine(maid)) return;
        ServerLevel level = (ServerLevel) maid.level();
        Control c = CONTROLS.computeIfAbsent(maid, ignored -> new Control());
        long now = level.getGameTime();
        // A native GUI/NoAI pause skips Brain ticks. Resume by ending this session, not replaying it.
        if (c.lastTick >= 0 && now - c.lastTick > 1) {
            clearWalk(maid, c); wake(maid); c.sitUntil = 0;
            WanderingCampData.data(maid).remove("SitPendingUntil");
            c.sleepAbandoned = c.sleepEntered;
            c.mode = "";
        }
        c.lastTick = now;
        if (blocked(maid)) {
            clearWalk(maid, c); wake(maid); c.sitUntil = 0;
            WanderingCampData.data(maid).remove("SitPendingUntil");
            WanderingCampManager.interrupt(maid);
            return;
        }
        decide(maid, c, level);
        if (!maid.isSleeping()) WanderingCampGly.tick(maid);
    }

    private static void decide(EntityMaid maid, Control c, ServerLevel level) {
        CampRoutineProfile profile = CampRoutineProfile.bound(maid).orElseThrow();
        CompoundTag d = WanderingCampData.data(maid);
        long now = level.getGameTime(), time = Math.floorMod(level.getDayTime(), 24000), day = Math.floorDiv(level.getDayTime(), 24000);
        boolean panic = now < d.getLong("PanicUntil");
        // Persist window consumption before any movement; missed windows during danger are not replayed.
        for (int i = 0; i < profile.sitTimes().size(); i++) {
            int slot = profile.sitTimes().get(i);
            if (time >= slot && time < slot + 40 && (!d.contains("SitDay" + i) || d.getLong("SitDay" + i) != day)) {
                d.putLong("SitDay" + i, day);
                if (!panic && !d.hasUUID("Inviter") && !maid.isSleeping() && !maid.getSwimManager().isGoingToBreath())
                    d.putLong("SitPendingUntil", now + 200);
            }
        }
        if (c.sleepDay != day) {
            c.sleepDay = day; c.sleepEntered = false; c.sleepAbandoned = false;
        }
        // An unrelated movement owner wins. Do not repeatedly overwrite it with our walk target.
        WalkTarget current = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (current != null && current != c.walk && !maid.getSwimManager().isGoingToBreath()) {
            c.walk = null; endSit(maid); c.sitUntil = 0; d.remove("SitPendingUntil");
            if (profile.night(level.getDayTime())) c.sleepAbandoned = true;
            return;
        }
        // A native emergency breath path is a basic survival capability, not domestic AI fallback.
        // Do not replace it with a camp movement target while it owns navigation.
        if (maid.getSwimManager().isGoingToBreath()) {
            changeMode(maid, c, "breath");
            return;
        }
        if (panic) {
            changeMode(maid, c, "panic");
            wake(maid);
            if (c.sleepEntered) c.sleepAbandoned = true;
            d.remove("SitPendingUntil"); c.sitUntil = 0;
            if (now >= c.nextMove) {
                c.nextMove = now + 20;
                BlockPos danger = BlockPos.of(d.getLong("Danger"));
                Entity attacker = d.hasUUID("Attacker") ? level.getEntity(d.getUUID("Attacker")) : null;
                if (attacker != null) danger = attacker.blockPosition();
                moveCandidate(maid, c, danger, true, false);
            }
            return;
        }
        if (d.hasUUID("Inviter")) {
            changeMode(maid, c, "invite");
            wake(maid);
            // The existing invitation transaction advances through MaidTickEvent, not movement eligibility.
            return;
        }
        if (!maid.isSleeping() && now >= c.nextPickup) {
            c.nextPickup = now + 10;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, maid.getBoundingBox().inflate(1),
                    e -> !e.hasPickUpDelay() && (e.getItem().isEdible() || e.getItem().is(Items.CAKE))))
                maid.pickupItem(item, false); // native insertion and actual-quantity credit hook
        }
        if (profile.night(level.getDayTime())) {
            changeMode(maid, c, "sleep");
            d.remove("SitPendingUntil"); c.sitUntil = 0;
            if (maid.isSleeping()) {
                if (!CampSleepPlacement.eligible(maid)) { wake(maid); c.sleepAbandoned = true; }
                return;
            }
            // A safety veto or external wake ends this night's attempt; never re-enter every tick.
            if (c.sleepEntered) c.sleepAbandoned = true;
            if (c.sleepAbandoned) return;
            if (CampSleepPlacement.eligible(maid) && nearPoint(maid, profile.head())) {
                clearWalk(maid, c);
                c.sleepEntered = true;
                maid.startSleeping(profile.head());
            } else if (now >= c.nextMove && current == null) {
                c.nextMove = now + 40;
                if (++c.attempts > 3) { c.sleepAbandoned = true; return; }
                if (!beddingValid(maid) || !walk(maid, c, profile.head(), .45F)) {
                    if (c.attempts >= 3) c.sleepAbandoned = true;
                }
            }
            return;
        }
        if (maid.isSleeping()) wake(maid);
        if (d.getLong("SitPendingUntil") > now || c.sitUntil > now) {
            changeMode(maid, c, "sit");
            if (c.sitUntil > now) {
                if (!maid.isInSittingPose()) { c.sitUntil = 0; endSit(maid); }
                return;
            }
            if (safeStand(maid, profile.sit()) && nearPoint(maid, profile.sit())
                    && !maid.isOrderedToSit() && !maid.isInSittingPose()) {
                clearWalk(maid, c);
                c.sitUntil = now + WanderingCampManager.SIT_TICKS;
                d.remove("SitPendingUntil");
                d.putBoolean("CampSit", true);
                // Keep TLM's coupled command-sit for this timed rest to prevent seated sliding.
                // endSit owns its release on timeout, damage, invitation and other transitions.
                maid.setInSittingPose(true);
                maid.setPose(Pose.SITTING);
            } else if (now >= c.nextMove && current == null) {
                c.nextMove = now + 40;
                if (++c.attempts > 3) { d.remove("SitPendingUntil"); return; }
                walk(maid, c, profile.sit(), .4F);
            }
            return;
        }
        changeMode(maid, c, "idle");
        if (now < c.nextMove || current != null || maid.isOrderedToSit()) return;
        c.nextMove = now + 60 + maid.getRandom().nextInt(40);
        boolean outside = !inCamp(maid, maid.blockPosition());
        moveCandidate(maid, c, WanderingCampData.head(maid), false, outside);
    }

    private static void changeMode(EntityMaid maid, Control c, String mode) {
        if (c.mode.equals(mode)) return;
        clearWalk(maid, c);
        endSit(maid);
        c.mode = mode; c.nextMove = 0; c.attempts = 0;
    }
    public static void release(EntityMaid maid, boolean wake) {
        Control c = CONTROLS.remove(maid);
        if (c != null) clearWalk(maid, c);
        if (wake) wake(maid);
    }
    private static void wake(EntityMaid maid) {
        if (WanderingCampData.sleepingOnBedding(maid)) maid.stopSleeping();
        endSit(maid);
    }
    private static void endSit(EntityMaid maid) {
        CompoundTag d = WanderingCampData.data(maid);
        if (d.getBoolean("CampSit")) {
            maid.setInSittingPose(false);
            if (maid.getPose() == Pose.SITTING) maid.setPose(Pose.STANDING);
            d.remove("CampSit");
        }
    }
    private static void clearWalk(EntityMaid maid, Control c) {
        // Never erase a path taken over by eating, external control or another legitimate behavior.
        if (c.walk != null && maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == c.walk) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.PATH);
            maid.getNavigation().stop();
        }
        c.walk = null;
    }
    public static boolean inCamp(EntityMaid maid, BlockPos p) {
        var profile = CampRoutineProfile.bound(maid);
        if (profile.isEmpty()) return false;
        BlockPos h = profile.get().center();
        return Math.abs(p.getY() - h.getY()) <= 2 &&
                Math.pow(p.getX()-h.getX(), 2) + Math.pow(p.getZ()-h.getZ(), 2) <= profile.get().radius() * profile.get().radius();
    }
    public static boolean beddingValid(EntityMaid maid) { return CampSleepPlacement.beddingValid(maid); }
    private static boolean nearPoint(EntityMaid maid, BlockPos p) {
        double offset = maid.level().getBlockState(p).is(Blocks.YELLOW_CARPET) ? .0625 : 0;
        return maid.position().distanceToSqr(new Vec3(p.getX()+.5, p.getY()+offset, p.getZ()+.5)) < .64;
    }
    public static boolean hazard(EntityMaid maid, BlockPos pos) {
        var state = maid.level().getBlockState(pos);
        return !state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                || (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT));
    }
    public static boolean safeStand(EntityMaid maid, BlockPos p) {
        if (!maid.level().hasChunkAt(p) || hazard(maid,p) || hazard(maid,p.above()) || hazard(maid,p.below())) return false;
        var level = maid.level();
        if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) return false;
        double surface = level.getBlockState(p).is(Blocks.YELLOW_CARPET) ? .0625 : 0;
        var box = maid.getType().getDimensions().makeBoundingBox(p.getX()+.5, p.getY()+surface, p.getZ()+.5).deflate(.01);
        return level.getBlockCollisions(maid, box).iterator().hasNext() == false;
    }
    private static boolean walk(EntityMaid maid, Control c, BlockPos p, float speed) {
        WalkTarget current = maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (current != null && current != c.walk || !safeStand(maid,p)) return false;
        Path path = maid.getNavigation().createPath(p, 0);
        if (path == null || !path.canReach() || path.getEndNode() == null || path.getEndNode().asBlockPos().distSqr(p) > 1) return false;
        for (int i=1; i<path.getNodeCount(); i++) {
            BlockPos n = path.getNode(i).asBlockPos();
            if (hazard(maid,n) || hazard(maid,n.below()) || path.getNode(i-1).y - path.getNode(i).y > 2) return false;
        }
        clearWalk(maid,c);
        c.walk = new WalkTarget(p, speed, 0);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, c.walk);
        return true;
    }
    private static void moveCandidate(EntityMaid maid, Control c, BlockPos anchor, boolean flee, boolean nearCenter) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos center = flee ? maid.blockPosition() : CampRoutineProfile.bound(maid).orElseThrow().center();
        int range = flee ? 6 : (nearCenter ? 3 : WanderingCampManager.RADIUS);
        for (int i=0; i<12; i++) {
            BlockPos p = center.offset(maid.getRandom().nextInt(range*2+1)-range, maid.getRandom().nextInt(5)-2,
                    maid.getRandom().nextInt(range*2+1)-range);
            if ((flee || inCamp(maid,p)) && safeStand(maid,p)) candidates.add(p);
        }
        if (flee) candidates.sort(Comparator.comparingDouble((BlockPos p) -> p.distSqr(anchor)).reversed());
        int checks = 0;
        for (BlockPos p : candidates) {
            if (flee && p.distSqr(anchor) <= maid.blockPosition().distSqr(anchor) + 2) continue;
            if (++checks > 4) break;
            if (walk(maid,c,p,flee ? .8F : .35F)) return;
        }
    }
}
