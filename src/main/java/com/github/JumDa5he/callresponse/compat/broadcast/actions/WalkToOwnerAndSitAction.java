package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

public class WalkToOwnerAndSitAction {

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        if (maid.getOwner() == null) {
            MaidResponder.debug(debugPlayer, "§c[调试] 女仆没有主人");
            return;
        }
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";
        BroadcastMovementScheduler.startWalk(maid, maid.getOwner(), debugPlayer,
                BroadcastMovementScheduler.WalkKind.SIT);
        MaidResponder.debug(debugPlayer, "§a[动作] " + name + " 正在前往主人");
    }

    static void onArrived(EntityMaid maid, ServerPlayer debugPlayer) {
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : "无名";
        // 这是明确广播动作的最终结果，允许原版 Sitting 永久落盘。
        maid.setInSittingPose(true);
        MaidResponder.debug(debugPlayer, "§a[调试] " + name + " 已到达主人身边并坐下");
    }
}
