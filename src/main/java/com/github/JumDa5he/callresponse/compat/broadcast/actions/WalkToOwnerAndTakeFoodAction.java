package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.hunger.HungerEatingGuard;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class WalkToOwnerAndTakeFoodAction {
    private WalkToOwnerAndTakeFoodAction() {
    }

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        if (HungerEatingGuard.isBlocked(maid)) {
            MaidResponder.debug(debugPlayer, "§e[调试] 女仆当前被禁止主动进食");
            return;
        }
        if (!(maid.getOwner() instanceof Player owner)) {
            debugPlayer.sendSystemMessage(Component.literal("§c[动作] 没有主人！"));
            MaidResponder.debug(debugPlayer, "§c[调试] 女仆没有主人");
            return;
        }
        ItemStack food = owner.getMainHandItem();
        if (food.isEmpty() || food.get(DataComponents.FOOD) == null) {
            debugPlayer.sendSystemMessage(Component.literal("§c[动作] 主人手里没有食物！"));
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
        if (currentFood.isEmpty() || currentFood.get(DataComponents.FOOD) == null) {
            debugPlayer.sendSystemMessage(Component.literal("§c[动作] 主人手里没有食物了！"));
            MaidResponder.debug(debugPlayer, "§c[调试] 主人手里没有食物了");
            return;
        }

        ItemStack previous = maid.getMainHandItem();
        int stashSlot = -1;
        if (!previous.isEmpty()) {
            var hideInv = maid.getHideInv();
            for (int slot = 0; slot < hideInv.size(); slot++) {
                if (ItemsUtil.extractItem(hideInv, slot, 1, true, null).isEmpty()) {
                    ItemsUtil.setStackInSlot(hideInv, slot, previous.copy());
                    stashSlot = slot;
                    break;
                }
            }
            if (stashSlot < 0) {
                debugPlayer.sendSystemMessage(Component.literal("§c[动作] 隐藏物品栏已满，无法暂存手中的物品！"));
                MaidResponder.debug(debugPlayer, "§c[调试] " + name + " 隐藏物品栏已满，取食物失败");
                return;
            }
        }

        ItemStack foodToTake = currentFood.copyWithCount(1);
        currentFood.shrink(1);
        String foodName = foodToTake.getDisplayName().getString();
        maid.setItemInHand(InteractionHand.MAIN_HAND, foodToTake);
        ItemStack foodRemainder = foodToTake.finishUsingItem(maid.level(), maid);

        if (stashSlot >= 0) {
            if (!foodRemainder.isEmpty()) {
                ItemStack inventoryRemainder = ItemsUtil.insertItemStacked(
                        maid.getMaidInv(), foodRemainder, false, null);
                if (!inventoryRemainder.isEmpty() && maid.level() instanceof net.minecraft.server.level.ServerLevel level) {
                    maid.spawnAtLocation(level, inventoryRemainder);
                }
            }
            ItemStack restored = ItemsUtil.extractItem(
                    maid.getHideInv(), stashSlot, Integer.MAX_VALUE, false, null);
            maid.setItemInHand(InteractionHand.MAIN_HAND, restored);
        } else {
            maid.setItemInHand(InteractionHand.MAIN_HAND, foodRemainder);
        }
        debugPlayer.sendSystemMessage(Component.literal("§a[动作] " + name + " 从主人手中拿到并吃掉了 " + foodName));
        MaidResponder.debug(debugPlayer, "§a[调试] " + name + " 已拿到并吃掉食物");
        maid.setInSittingPose(true);
    }
}
