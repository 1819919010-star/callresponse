package com.github.JumDa5he.callresponse.compat.trade;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Marks the next merchant screen as belonging to a wandering trader. */
public final class EnableTradingMaidButtonS2CPacket {
    private final int traderId;

    public EnableTradingMaidButtonS2CPacket(int traderId) {
        this.traderId = traderId;
    }

    public EnableTradingMaidButtonS2CPacket(FriendlyByteBuf buf) {
        this.traderId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(traderId);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> TradingMaidClientState.markTrader(traderId));
        context.setPacketHandled(true);
    }
}
