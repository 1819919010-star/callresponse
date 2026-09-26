package com.github.JumDa5he.callresponse.compat.brain;

import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskFeedOwner;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.UUID;

/** 喂食工作对低饥饿同伴的轻量扩展：送入一份食物，实际进食仍交给 HungerManager。 */
public final class FeedHungryMaidBehavior extends Behavior<EntityMaid> {
    private static final float TARGET_HUNGER = 30.0f;
    private static final double SEARCH_RADIUS = 12.0;
    private static final double ARRIVE_DISTANCE_SQR = 2.0 * 2.0;

    private @Nullable EntityMaid target;
    private @Nullable WalkTarget issuedWalkTarget;
    private boolean finished;
    private long nextCheckTime;

    public FeedHungryMaidBehavior() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.HURT_BY_ENTITY, MemoryStatus.VALUE_ABSENT
        ), 20 * 12);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        long gameTime = level.getGameTime();
        if (gameTime < nextCheckTime || !canCareForMaid(maid) || ownerNeedsPriorityCare(maid)) {
            return false;
        }
        nextCheckTime = gameTime + 40 + maid.getRandom().nextInt(21);
        if (!hasFood(maid.getAvailableInv(true), maid)) return false;
        target = findTarget(level, maid);
        return target != null;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        if (target == null) return;
        finished = false;
        issuedWalkTarget = new WalkTarget(new EntityTracker(target, true), 0.6f, 1);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, issuedWalkTarget);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (!validTarget(maid, target)) {
            finished = true;
            return;
        }
        if (maid.distanceToSqr(target) <= ARRIVE_DISTANCE_SQR) {
            SeekFoodBehavior.transferOne(maid.getAvailableInv(true), target);
            maid.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            finished = true;
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        return !finished && canCareForMaid(maid) && validTarget(maid, target)
                && hasFood(maid.getAvailableInv(true), target);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .filter(walkTarget -> walkTarget == issuedWalkTarget)
                .ifPresent(walkTarget -> maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET));
        target = null;
        issuedWalkTarget = null;
        finished = false;
    }

    private static boolean canCareForMaid(EntityMaid maid) {
        return maid.isAlive() && maid.isTame() && maid.getOwnerUUID() != null
                && TaskFeedOwner.UID.equals(maid.getTask().getUid())
                && HungerData.get(maid) >= 15.0f
                && maid.canBrainMoving()
                && !HuntOrderManager.isHunting(maid)
                && !MaidMovementControl.controlsPath(maid);
    }

    private static boolean ownerNeedsPriorityCare(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (!(owner instanceof Player player) || !player.isAlive()) return true;
        if (player.getFoodData().needsFood() || player.getHealth() < player.getMaxHealth() * 0.5f) return true;
        return player.getActiveEffects().stream()
                .anyMatch(effect -> effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL
                        && effect.getDuration() > 60);
    }

    @Nullable
    private static EntityMaid findTarget(ServerLevel level, EntityMaid feeder) {
        UUID ownerId = feeder.getOwnerUUID();
        AABB area = feeder.getBoundingBox().inflate(SEARCH_RADIUS, 4.0, SEARCH_RADIUS);
        return level.getEntitiesOfClass(EntityMaid.class, area, maid -> validTarget(feeder, maid))
                .stream().min(Comparator.comparingDouble(feeder::distanceToSqr)).orElse(null);
    }

    private static boolean validTarget(EntityMaid feeder, @Nullable EntityMaid target) {
        return target != null && target != feeder && target.isAlive() && target.isTame()
                && feeder.getOwnerUUID() != null && feeder.getOwnerUUID().equals(target.getOwnerUUID())
                && HungerData.get(target) < TARGET_HUNGER
                && SeekFoodBehavior.hasBackpackSpace(target)
                && !SeekFoodBehavior.isSeeking(target)
                && feeder.isWithinRestriction(target.blockPosition());
    }

    private static boolean hasFood(IItemHandler inventory, EntityMaid receiver) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getFoodProperties(receiver) != null) return true;
        }
        return false;
    }
}
