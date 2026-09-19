package com.github.JumDa5he.callresponse.compat.task;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

/** 只负责按钮触发后的靠近流程；真正抱持由原版骑乘同步完成。 */
public final class PrincessCarryBehavior extends Behavior<EntityMaid> {
    private static final float WALK_SPEED = 0.6F;
    private static final double PICKUP_DISTANCE_SQR = 2.25D;

    public PrincessCarryBehavior() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), 600);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return PrincessCarryManager.getRequestedTarget(level, maid) != null
                && PrincessCarryTask.isCurrentTask(maid)
                && PrincessCarryTask.hasSaddle(maid)
                && maid.canBrainMoving()
                && maid.getPassengers().isEmpty();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        LivingEntity target = PrincessCarryManager.getRequestedTarget(level, maid);
        return target != null && PrincessCarryTask.isCurrentTask(maid)
                && PrincessCarryTask.hasSaddle(maid) && maid.getPassengers().isEmpty();
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        moveOrPickup(level, maid);
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        moveOrPickup(level, maid);
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.getPassengers().isEmpty()) {
            PrincessCarryManager.cancelRequest(maid);
        }
    }

    private static void moveOrPickup(ServerLevel level, EntityMaid maid) {
        LivingEntity target = PrincessCarryManager.getRequestedTarget(level, maid);
        if (target == null) return;
        if (maid.distanceToSqr(target) <= PICKUP_DISTANCE_SQR) {
            // 抱起后 carrier 仍需继续跟随和寻路，不能清掉她自己的导航或 WALK_TARGET。
            PrincessCarryManager.finishPickup(maid, target);
            return;
        }
        BehaviorUtils.setWalkAndLookTargetMemories(maid, target, WALK_SPEED, 1);
    }
}
