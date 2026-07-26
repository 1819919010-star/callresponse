package com.github.tartaricacid.callresponse.compat.broadcast.actions;

import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Timer;
import java.util.TimerTask;

public class WalkToOwnerAndTakeFoodAction {

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

        Player owner = (Player) maid.getOwner();
        ItemStack foodInHand = owner.getMainHandItem();
        if (foodInHand.isEmpty() || !foodInHand.getItem().isEdible()) {
            maid.sendSystemMessage(Component.literal("§c[动作] 主人手里没有食物！"));
            MaidResponder.debug(debugPlayer, "§c[调试] 主人手里没有食物");
            return;
        }

        Vec3 ownerPos = owner.position();
        BlockPos targetPos = new BlockPos((int) ownerPos.x, (int) ownerPos.y, (int) ownerPos.z);
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";

        if (isSitting(maid)) standUp(maid);

        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);

        WalkTarget walkTarget = new WalkTarget(new BlockPosTracker(targetPos), 0.7f, 0);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
        maid.setSpeed(0.7f);

        MaidResponder.debug(debugPlayer, "§a[动作] " + name + " 正在前往主人取食物 (" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ")");

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
                        ItemStack currentFood = owner.getMainHandItem();
                        if (currentFood.isEmpty() || !currentFood.getItem().isEdible()) {
                            maid.sendSystemMessage(Component.literal("§c[动作] 主人手里没有食物了！"));
                            MaidResponder.debug(debugPlayer, "§c[调试] 主人手里没有食物了");
                            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                            maid.getNavigation().stop();
                            return;
                        }

                        ItemStack foodToTake = currentFood.copy();
                        foodToTake.setCount(1);
                        owner.getMainHandItem().shrink(1);

                        maid.setItemInHand(InteractionHand.MAIN_HAND, foodToTake);
                        maid.sendSystemMessage(Component.literal("§a[动作] " + name + " 从主人手中拿到了 " + foodToTake.getDisplayName().getString()));
                        MaidResponder.debug(debugPlayer, "§a[调试] " + name + " 已拿到食物");

                        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                        maid.getNavigation().stop();
                        sitDown(maid);
                    });
                    this.cancel();
                }
            }
        };
        TIMER.scheduleAtFixedRate(task, 200, 200);
    }
}