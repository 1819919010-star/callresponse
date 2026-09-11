package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StandUpAction {

    private static boolean isSitting(EntityMaid maid) {
        return maid.isInSittingPose();
    }

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        if (isSitting(maid)) {
            maid.setInSittingPose(false);
            String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";
            maid.sendSystemMessage(Component.translatable("message.callresponse.action.stood", name));
            MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.stood", name));
        } else {
            MaidResponder.debug(debugPlayer, Component.translatable("message.callresponse.debug.already_standing"));
        }
    }
}