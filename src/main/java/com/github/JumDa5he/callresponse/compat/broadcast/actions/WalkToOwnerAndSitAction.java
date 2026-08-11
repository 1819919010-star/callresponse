package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

import java.util.Timer;
import java.util.TimerTask;

public class WalkToOwnerAndSitAction {

    private static final Timer TIMER = new Timer(true);

    private static boolean isSitting(EntityMaid maid) {
        return maid.isInSittingPose();
    }

    private static void standUp(EntityMaid maid) {
        maid.setInSittingPose(false);
    }

    private static void sitDown(EntityMaid maid) {
        maid.setInSittingPose(true);
    }

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        if (maid.getOwner() == null) {
            maid.sendSystemMessage(Component.literal("§c[动作] 没有主人！"));
            MaidResponder.debug(debugPlayer, "§c[调试] 女仆没有主人");
            return;
        }

        Vec3 ownerPos = maid.getOwner().position();
        BlockPos targetPos = new BlockPos((int) ownerPos.x, (int) ownerPos.y, (int) ownerPos.z);
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";

        if (isSitting(maid)) standUp(maid);

        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);

        WalkTarget walkTarget = new WalkTarget(new BlockPosTracker(targetPos), 0.7f, 0);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
        maid.setSpeed(0.7f);

        MaidResponder.debug(debugPlayer, "§a[动作] " + name + " 正在前往主人 (" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ")");

        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (!maid.isAlive()) {
                    this.cancel();
                    return;
                }
                double distSq = maid.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
                if (distSq < 2.25) {
                    maid.getServer().execute(() -> {
                        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                        maid.getNavigation().stop();
                        sitDown(maid);
                        MaidResponder.debug(debugPlayer, "§a[调试] " + name + " 已到达主人身边并坐下");
                    });
                    this.cancel();
                }
            }
        };
        TIMER.scheduleAtFixedRate(task, 200, 200);
    }
}