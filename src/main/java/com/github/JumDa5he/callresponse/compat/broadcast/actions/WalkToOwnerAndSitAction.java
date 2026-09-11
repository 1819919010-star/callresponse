package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class WalkToOwnerAndSitAction {

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        if (maid.getOwner() == null) {
            maid.sendSystemMessage(Component.translatable("message.callresponse.action.no_owner"));
            MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.maid_no_owner"));
            return;
        }

        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";
        BroadcastMovementScheduler.startWalk(maid, maid.getOwner(), debugPlayer,
                BroadcastMovementScheduler.WalkKind.SIT);
        MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.going_to_owner", name));
    }

    static void onArrived(EntityMaid maid, ServerPlayer debugPlayer) {
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";
        // 这是明确广播动作的最终结果，允许原版 Sitting 永久落盘。
        maid.setInSittingPose(true);
        MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.reached_owner", name));
    }
}
