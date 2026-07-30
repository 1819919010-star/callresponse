package com.github.tartaricacid.callresponse.compat.broadcast.actions;

import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SitDownAction {

    private static boolean isSitting(EntityMaid maid) {
        return maid.isInSittingPose();
    }

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        if (!isSitting(maid)) {
            maid.setInSittingPose(true);
            Component name = maid.getName();
            MaidResponder.debug(debugPlayer, Component.literal("§a[调试] ").append(name).append(" 执行: 坐下"));
        } else {
            MaidResponder.debug(debugPlayer, "§e[调试] 已经坐下了");
        }
    }
}