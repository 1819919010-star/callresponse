package com.github.JumDa5he.callresponse.compat.trade;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class RequestTradingMaidScreenC2SPacket {
    private final int traderId;

    public RequestTradingMaidScreenC2SPacket(int traderId) {
        this.traderId = traderId;
    }

    public RequestTradingMaidScreenC2SPacket(FriendlyByteBuf buf) {
        this.traderId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(traderId);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                TradingMaidManager.sendTradingScreen(context.getSender(), traderId);
            }
        });
        context.setPacketHandled(true);
    }
}
