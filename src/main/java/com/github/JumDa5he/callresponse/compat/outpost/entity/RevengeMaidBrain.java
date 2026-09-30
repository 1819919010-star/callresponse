package com.github.JumDa5he.callresponse.compat.outpost.entity;

import com.github.JumDa5he.callresponse.compat.outpost.*;
import com.github.JumDa5he.callresponse.compat.brain.FeedHungryMaidBehavior;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.*;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskNormalFarm;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import java.util.*;

/** No MaidBrain.registerBrainGoals, no owner follow, panic, domestic schedule or auto-teleport. */
public final class RevengeMaidBrain {
    private RevengeMaidBrain() {}

    public static Brain.Provider<EntityMaid> provider() {
        return Brain.provider(List.of(MemoryModuleType.PATH, MemoryModuleType.DOORS_TO_CLOSE,
                MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.ATTACK_TARGET,
                MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.HURT_BY,
                MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.NEAREST_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, InitEntities.TARGET_POS.get(),
                InitEntities.MAID_EDIBLE_BLOCK_ACTION.get()),
                List.of(InitEntities.MAID_NEAREST_LIVING_ENTITY_SENSOR.get(), SensorType.HURT_BY));
    }

    public static void register(Brain<EntityMaid> brain) {
        brain.setSchedule(InitEntities.MAID_DAY_SHIFT_SCHEDULES.get());
        brain.addActivity(Activity.CORE, ImmutableList.of(
                Pair.of(1, new MaidSwimJumpTask(0.8F)), Pair.of(1, new MaidBreathAirTask()),
                Pair.of(1, new MaidBreathAirStopTask()), Pair.of(1, new MaidClimbTask()),
                Pair.of(2, new LookAtTargetSink(45, 90)), Pair.of(2, MaidInteractWithDoor.create()),
                Pair.of(3, new MoveToTargetSink()), Pair.of(4, new MaidHealSelfTask()),
                Pair.of(4, new MaidWorkMealTask())));
        brain.addActivity(Activity.FIGHT, ImmutableList.of(
                Pair.of(5, MaidMeleeAttack.create(20)), Pair.of(5, new MaidUseShieldTask() {
                    @Override protected void stop(ServerLevel level, EntityMaid maid, long now) {
                        // Losing the shield to a meal must not cancel the meal in the same tick.
                        if (maid.isUsingItem() && maid.getUseItem().canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK))
                            super.stop(level, maid, now);
                    }
                }),
                Pair.of(5, new MaidShootTargetTask() {
                    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
                        return maid.getMainHandItem().getItem() instanceof BowItem && super.checkExtraStartConditions(level, maid);
                    }
                }),
                Pair.of(5, new MaidCrossbowAttack())));
        // The activity selector enables WORK only for the matching fixed role.
        // Role guards avoid giving the feeder a farm task, without constructing from uninitialized entity fields.
        var farm = new TaskNormalFarm();
        brain.addActivity(Activity.WORK, ImmutableList.of(
                Pair.of(5, new MaidFarmMoveTask(farm, 0.6F) {
                    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
                        return maid.canBrainMoving() && BetrayalOutpostMaidData.role(maid) == BetrayalOutpostMaidData.Role.FARMER
                                && super.checkExtraStartConditions(level, maid);
                    }
                }),
                Pair.of(6, new MaidFarmPlantTask(farm) {
                    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
                        return maid.canBrainMoving() && BetrayalOutpostMaidData.role(maid) == BetrayalOutpostMaidData.Role.FARMER
                                && super.checkExtraStartConditions(level, maid);
                    }
                }),
                Pair.of(7, new FeedHungryMaidBehavior()),
                Pair.of(20, wander())));
        brain.addActivity(Activity.IDLE, ImmutableList.of(Pair.of(5, new RevengeMaidRest(false)), Pair.of(20, wander())));
        brain.addActivity(Activity.REST, ImmutableList.of(Pair.of(5, new RevengeMaidRest())));
        // Empty fallback also suspends role/idle tasks during one-shot home return or GLY fleeing.
        brain.addActivity(Activity.PANIC, ImmutableList.of());
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
    }

    public static boolean externallyControlled(RevengeMaidEntity maid) {
        return maid.isNoAi() || IntimidationManager.isIntimidated(maid)
                || MaidMovementControl.controlsOther(maid, MaidMovementControl.Reason.OUTPOST_GLY_FLEE, MaidMovementControl.Field.PATH)
                || PrincessCarryManager.isMaidCarrySession(maid)
                || (maid.isPassenger() && !(maid.getVehicle() instanceof EntitySit)
                    && !BetrayalOutpostStackManager.isMovementDelegated(maid));
    }

    public static void tick(RevengeMaidEntity maid) {
        // Decide BEFORE Brain starts/ticks activities; a former leisure task must not run after combat takes over.
        if (!maid.safety.tick(maid)) decide(maid);
        maid.getBrain().tick((ServerLevel) maid.level(), maid);
    }

    private static void decide(RevengeMaidEntity maid) {
        var brain = maid.getBrain();
        if (BetrayalOutpostMaidData.group(maid).startsWith("standalone:") && maid.tickCount % 100 == 0)
            BetrayalOutpostManager.personalizeStandalone(maid);
        // Keep the existing day-shift time rules, but choose activities ourselves (no domestic updater).
        Activity scheduled = maid.getScheduleDetail();
        if (scheduled == Activity.REST || maid.isSleeping()) EmotionBetrayalManager.cancelOutpostReturn(maid);
        if (maid.isSleeping() && (scheduled != Activity.REST || maid.getSleepingPos()
                .map(pos -> !(maid.level().getBlockState(pos).getBlock() instanceof com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed))
                .orElse(true))) maid.stopSleeping();
        if (BetrayalOutpostMaidData.isGly(maid)) {
            if (maid.isSleeping() && MaidMovementControl.isActive(maid, MaidMovementControl.Reason.OUTPOST_GLY_FLEE))
                maid.stopSleeping(); // Actual hurt-created flee state, never a routine idle wake-up.
            if (!maid.isSleeping()) {
                BetrayalOutpostMaidData.tickGlyDialogue(maid);
                BetrayalOutpostMaidData.tickGlyBehavior(maid);
            }
            if (MaidMovementControl.isActive(maid, MaidMovementControl.Reason.OUTPOST_GLY_FLEE)) {
                brain.setActiveActivityIfPossible(Activity.PANIC);
                return;
            }
            EmotionBetrayalManager.updateOutpostReturn(maid);
        } else {
            BetrayalOutpostAlertManager.tick(maid);
            EmotionBetrayalManager.tickOutpostCombat(maid);
            BetrayalOutpostStackManager.tick(maid);
        }
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            brain.setActiveActivityIfPossible(Activity.FIGHT);
            return;
        }
        if (BetrayalOutpostAlertManager.isCombatRecovery(maid)) {
            // No leisure/bed/work task can reacquire a seat during the three-second quiet window.
            brain.setActiveActivityIfPossible(Activity.PANIC);
            return;
        }
        if (EmotionBetrayalManager.isReturningToOutpost(maid)) {
            brain.setActiveActivityIfPossible(Activity.PANIC);
        } else if (scheduled == Activity.REST) {
            if (!brain.isActive(Activity.REST)) {
                // At this transition the previous WORK/IDLE activity owns these memories.
                // Do not repeat this while walking toward a bed or already sleeping.
                brain.eraseMemory(InitEntities.TARGET_POS.get());
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(MemoryModuleType.PATH);
                maid.getNavigation().stop();
                if (maid.getVehicle() instanceof EntitySit) maid.stopRiding();
            }
            brain.setActiveActivityIfPossible(Activity.REST);
        } else if (scheduled == Activity.WORK &&
                (BetrayalOutpostMaidData.role(maid) == BetrayalOutpostMaidData.Role.FARMER
                || BetrayalOutpostMaidData.role(maid) == BetrayalOutpostMaidData.Role.FEEDER)) {
            brain.setActiveActivityIfPossible(Activity.WORK);
        } else brain.setActiveActivityIfPossible(Activity.IDLE);
    }

    private static BehaviorControl<EntityMaid> wander() {
        return BehaviorBuilder.create(ctx -> ctx.point((level, maid, now) -> {
            if (maid.isSleeping() || !maid.canBrainMoving() || maid.isUsingItem() || (maid.tickCount + maid.getId()) % 80 != 0
                    || maid.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                    || maid.getBrain().hasMemoryValue(InitEntities.TARGET_POS.get())) return false;
            if (!(maid instanceof RevengeMaidEntity revenge)) return false;
            var anchor = revenge.getCampActivityAnchor();
            var candidates = new java.util.HashSet<net.minecraft.core.BlockPos>();
            // Routine activity is biased to the entrance/ground floor, not a forced return task.
            // Occasionally visit the upper floor; beds, work and combat retain their own destinations.
            boolean upstairs = maid.getRandom().nextInt(6) == 0;
            for (int attempt = 0; attempt < 20; attempt++) {
                var pos = anchor.offset(maid.getRandom().nextInt(21) - 10,
                        upstairs ? 5 + maid.getRandom().nextInt(7) : maid.getRandom().nextInt(3) - 1,
                        maid.getRandom().nextInt(21) - 10);
                if (maid.isWithinRestriction(pos) && (!upstairs || !level.canSeeSky(pos))
                        && RevengeMaidSafety.safeStanding(revenge, pos)) candidates.add(pos);
            }
            if (candidates.isEmpty()) return false;
            var path = maid.getNavigation().createPath(candidates, 0);
            if (path == null || !path.canReach() || path.getEndNode() == null
                    || !candidates.contains(path.getEndNode().asBlockPos())) return false;
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(path.getEndNode().asBlockPos(), 0.3F, 1));
            return true;
        }));
    }
}
