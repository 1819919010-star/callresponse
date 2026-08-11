package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
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
import net.neoforged.neoforge.items.ItemStackHandler;

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
            MaidResponder.debug(debugPlayer, "§c[调试] 女仆没有主人");
            return;
        }

        Player owner = (Player) maid.getOwner();
        ItemStack foodInHand = owner.getMainHandItem();
        if (foodInHand.isEmpty() || foodInHand.getFoodProperties(maid) == null) {
            MaidResponder.debug(debugPlayer, "§c[调试] 主人手里没有食物");
            return;
        }

        Vec3 ownerPos = owner.position();
        BlockPos targetPos = new BlockPos((int) ownerPos.x, (int) ownerPos.y, (int) ownerPos.z);
        Component name = maid.getName();

        if (isSitting(maid)) standUp(maid);

        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);

        WalkTarget walkTarget = new WalkTarget(new BlockPosTracker(targetPos), 0.7f, 0);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
        maid.setSpeed(0.7f);

        MaidResponder.debug(debugPlayer, Component.literal("§a[动作] ").append(name).append(" 正在前往主人取食物 (").append(targetPos.toShortString()).append(")"));

        TimerTask task = new TimerTask() {
            private ItemStack stashedItem = ItemStack.EMPTY;

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
                        if (currentFood.isEmpty() || currentFood.getFoodProperties(maid) == null) {
                            MaidResponder.debug(debugPlayer, "§c[调试] 主人手里没有食物了");
                            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                            maid.getNavigation().stop();
                            return;
                        }

                        ItemStack previous = maid.getMainHandItem();
                        if (!previous.isEmpty()) {
                            ItemStackHandler hideInv = maid.getHideInv();
                            boolean stashed = false;
                            for (int i = 0; i < hideInv.getSlots(); i++) {
                                if (hideInv.getStackInSlot(i).isEmpty()) {
                                    hideInv.setStackInSlot(i, previous);
                                    stashedItem = previous.copy();
                                    stashed = true;
                                    break;
                                }
                            }
                            if (!stashed) {
                                maid.sendSystemMessage(Component.literal("§c[动作] 隐藏物品栏已满，无法暂存手中的物品！"));
                                MaidResponder.debug(debugPlayer,
                                        Component.literal("§c[调试] ")
                                                .append(name)
                                                .append(Component.literal(" 隐藏物品栏已满，取食物失败"))
                                );
                                return;
                            }
                        } else {
                            stashedItem = ItemStack.EMPTY;
                        }

                        ItemStack foodToTake = currentFood.copy();
                        foodToTake.setCount(1);
                        owner.getMainHandItem().shrink(1);
                        String foodName = foodToTake.getDisplayName().getString();

                        maid.setItemInHand(InteractionHand.MAIN_HAND, foodToTake);
                        maid.eat(maid.level(), foodToTake);
                        foodToTake.shrink(1);

                        if (!stashedItem.isEmpty()) {
                            ItemStackHandler hideInv = maid.getHideInv();
                            for (int i = 0; i < hideInv.getSlots(); i++) {
                                if (ItemStack.isSameItemSameComponents(hideInv.getStackInSlot(i), stashedItem)) {
                                    maid.setItemInHand(InteractionHand.MAIN_HAND, hideInv.getStackInSlot(i));
                                    hideInv.setStackInSlot(i, ItemStack.EMPTY);
                                    break;
                                }
                            }
                            stashedItem = ItemStack.EMPTY;
                        }

                        maid.sendSystemMessage(Component.literal("§a[动作] ")
                                .append(name)
                                .append(Component.literal(" 从主人手中拿到并吃掉了 " + foodName)));
                        MaidResponder.debug(debugPlayer,
                                Component.literal("§a[调试] ")
                                        .append(name)
                                        .append(Component.literal(" 已拿到并吃掉食物"))
                        );

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
