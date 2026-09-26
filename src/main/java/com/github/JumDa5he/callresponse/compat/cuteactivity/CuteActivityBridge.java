package com.github.JumDa5he.callresponse.compat.cuteactivity;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.PacketDistributor;

/** Optional, server-side boundary. No Cute Activity class is linked from common code. */
public final class CuteActivityBridge {
    public static final String MOD_ID = "maid_cute_activity";

    private CuteActivityBridge() {
    }

    public static boolean installed() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static boolean isRevengeMaid(EntityMaid maid) {
        return BetrayalOutpostMaidData.isOutpostMaid(maid);
    }

    /** duration=0 withdraws only callresponse's scare source, never Cute Activity's hurt source. */
    public static void syncToTrackers(EntityMaid maid, int remainingTicks) {
        if (!installed()) return;
        CallResponseMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> maid),
                new CuteActivityScareS2CPacket(maid.getUUID(), isRevengeMaid(maid), remainingTicks));
    }

    public static void syncToPlayer(EntityMaid maid, ServerPlayer player, int remainingTicks) {
        if (!installed()) return;
        CallResponseMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CuteActivityScareS2CPacket(maid.getUUID(), isRevengeMaid(maid), remainingTicks));
    }

    public static void clearForPlayer(EntityMaid maid, ServerPlayer player) {
        if (!installed()) return;
        CallResponseMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new CuteActivityScareS2CPacket(maid.getUUID(), false, 0));
    }
}
