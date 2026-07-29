package com.github.tartaricacid.callresponse.compat.broadcast.actions;

import com.github.tartaricacid.callresponse.compat.broadcast.MaidResponder;
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
            MaidResponder.debug(debugPlayer, "§a[调试] " + name + " 执行: 站起来");
        } else {
            MaidResponder.debug(debugPlayer, "§e[调试] 已经站着了");
        }
    }
}