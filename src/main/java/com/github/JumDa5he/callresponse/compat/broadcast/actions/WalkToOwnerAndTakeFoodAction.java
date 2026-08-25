package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.hunger.HungerEatingGuard;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class WalkToOwnerAndTakeFoodAction {

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        if (HungerEatingGuard.isBlocked(maid)) {
            MaidResponder.debug(debugPlayer, "§e[调试] 女仆当前被禁止主动进食");
            return;
        }
        if (!(maid.getOwner() instanceof Player owner)) {
            maid.sendSystemMessage(Component.literal("§c[动作] 没有主人！"));
            MaidResponder.debug(debugPlayer, "§c[调试] 女仆没有主人");
            return;
        }
        ItemStack food = owner.getMainHandItem();
        if (food.isEmpty() || food.getFoodProperties(maid) == null) {
            maid.sendSystemMessage(Component.literal("§c[动作] 主人手里没有食物！"));
            MaidResponder.debug(debugPlayer, "§c[调试] 主人手里没有食物");
            return;
        }
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";
        BroadcastMovementScheduler.startWalk(maid, owner, debugPlayer,
                BroadcastMovementScheduler.WalkKind.TAKE_FOOD);
        MaidResponder.debug(debugPlayer, "§a[动作] " + name + " 正在前往主人取食物");
    }

    static void onArrived(EntityMaid maid, LivingEntity ownerEntity, ServerPlayer debugPlayer) {
        if (!(ownerEntity instanceof Player owner) || HungerEatingGuard.isBlocked(maid)) {
            return;
        }
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";
        ItemStack currentFood = owner.getMainHandItem();
        if (currentFood.isEmpty() || currentFood.getFoodProperties(maid) == null) {
            maid.sendSystemMessage(Component.literal("§c[动作] 主人手里没有食物了！"));
            MaidResponder.debug(debugPlayer, "§c[调试] 主人手里没有食物了");
            return;
        }

        ItemStack previous = maid.getMainHandItem();
        ItemStack stashedItem = ItemStack.EMPTY;
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
                MaidResponder.debug(debugPlayer, "§c[调试] " + name + " 隐藏物品栏已满，取食物失败");
                return;
            }
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
        }
        maid.sendSystemMessage(Component.literal("§a[动作] " + name + " 从主人手中拿到并吃掉了 " + foodName));
        MaidResponder.debug(debugPlayer, "§a[调试] " + name + " 已拿到并吃掉食物");
        maid.setInSittingPose(true);
    }
}
