package com.github.JumDa5he.callresponse.compat.broadcast.actions;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

public class StopAttackAction {

    public static void execute(EntityMaid maid, ServerPlayer debugPlayer) {
        AttackOtherMaidAction.stopAllAttacks(maid, debugPlayer);
    }
}