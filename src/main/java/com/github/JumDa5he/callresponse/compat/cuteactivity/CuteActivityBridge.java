package com.github.JumDa5he.callresponse.compat.cuteactivity;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/** 可选联动边界：公共端不直接引用对方的类。 */
public final class CuteActivityBridge {
    public static final String MOD_ID = "maid_cute_activity";

    private CuteActivityBridge() {}

    public static boolean installed() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isRevengeMaid(EntityMaid maid) {
        return BetrayalOutpostMaidData.isOutpostMaid(maid);
    }

    public static void syncToTrackers(EntityMaid maid, int remainingTicks) {
        if (!installed()) return;
        PacketDistributor.sendToPlayersTrackingEntity(maid,
                new CuteActivityScareS2CPacket(maid.getUUID(), isRevengeMaid(maid), remainingTicks));
    }

    public static void syncToPlayer(EntityMaid maid, ServerPlayer player, int remainingTicks) {
        if (!installed()) return;
        PacketDistributor.sendToPlayer(player,
                new CuteActivityScareS2CPacket(maid.getUUID(), isRevengeMaid(maid), remainingTicks));
    }

    public static void clearForPlayer(EntityMaid maid, ServerPlayer player) {
        if (!installed()) return;
        PacketDistributor.sendToPlayer(player,
                new CuteActivityScareS2CPacket(maid.getUUID(), false, 0));
    }
}
