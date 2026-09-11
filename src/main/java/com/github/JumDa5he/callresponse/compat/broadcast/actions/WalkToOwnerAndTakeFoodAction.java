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
import net.minecraftforge.items.ItemStackHandler;

public class WalkToOwnerAndTakeFoodAction {
    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        if (HungerEatingGuard.isBlocked(maid)) {
            MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.eating_disabled"));
            return;
        }
        if (!(maid.getOwner() instanceof Player owner)) {
            maid.sendSystemMessage(Component.translatable("message.callresponse.action.no_owner"));
            MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.maid_no_owner"));
            return;
        }
        ItemStack food = owner.getMainHandItem();
        if (food.isEmpty() || !food.getItem().isEdible()) {
            maid.sendSystemMessage(Component.translatable("message.callresponse.action.owner_no_food"));
            MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.owner_no_food"));
            return;
        }
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";
        BroadcastMovementScheduler.startWalk(maid, owner, debugPlayer,
                BroadcastMovementScheduler.WalkKind.TAKE_FOOD);
        MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.going_for_food", name));
    }

    static void onArrived(EntityMaid maid, LivingEntity ownerEntity, ServerPlayer debugPlayer) {
        if (!(ownerEntity instanceof Player owner) || HungerEatingGuard.isBlocked(maid)) return;
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";
        ItemStack currentFood = owner.getMainHandItem();
        if (currentFood.isEmpty() || !currentFood.getItem().isEdible()) {
            maid.sendSystemMessage(Component.translatable("message.callresponse.action.owner_no_food_left"));
            MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.owner_no_food_left"));
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
                maid.sendSystemMessage(Component.translatable("message.callresponse.action.hidden_inventory_full"));
                MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.hidden_inventory_full", name));
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
                if (ItemStack.isSameItemSameTags(hideInv.getStackInSlot(i), stashedItem)) {
                    maid.setItemInHand(InteractionHand.MAIN_HAND, hideInv.getStackInSlot(i));
                    hideInv.setStackInSlot(i, ItemStack.EMPTY);
                    break;
                }
            }
        }
        maid.sendSystemMessage(Component.translatable("message.callresponse.action.ate_food", name, foodName));
        MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.ate_food", name));
        maid.setInSittingPose(true);
    }
}
