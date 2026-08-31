package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

public final class WalkToOwnerAndSitAction {
    private WalkToOwnerAndSitAction() {
    }

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        if (maid.getOwner() == null) {
            MaidResponder.debug(debugPlayer, "The maid has no owner");
            return;
        }
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : maid.getName().getString();
        BroadcastMovementScheduler.startWalk(maid, maid.getOwner(), debugPlayer,
                BroadcastMovementScheduler.WalkKind.SIT);
        MaidResponder.debug(debugPlayer, name + " is walking to the owner");
    }

    static void onArrived(EntityMaid maid, ServerPlayer debugPlayer) {
        String name = maid.getCustomName() != null ? maid.getCustomName().getString() : maid.getName().getString();
        maid.setInSittingPose(true);
        MaidResponder.debug(debugPlayer, name + " arrived and sat down");
    }
}
