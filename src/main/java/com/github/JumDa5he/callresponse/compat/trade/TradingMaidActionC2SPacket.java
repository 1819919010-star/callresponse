package com.github.JumDa5he.callresponse.compat.trade;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class TradingMaidActionC2SPacket {
    public enum Action { BUY, SELL }

    private final int traderId;
    private final UUID maidId;
    private final Action action;

    public TradingMaidActionC2SPacket(int traderId, UUID maidId, Action action) {
        this.traderId = traderId;
        this.maidId = maidId;
        this.action = action;
    }

    public TradingMaidActionC2SPacket(FriendlyByteBuf buf) {
        traderId = buf.readVarInt();
        maidId = buf.readUUID();
        action = buf.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(traderId);
        buf.writeUUID(maidId);
        buf.writeEnum(action);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                TradingMaidManager.handleTradingAction(context.getSender(), traderId, maidId, action);
            }
        });
        context.setPacketHandled(true);
    }
}
