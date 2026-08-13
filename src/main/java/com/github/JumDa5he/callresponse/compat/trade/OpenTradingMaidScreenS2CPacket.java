package com.github.JumDa5he.callresponse.compat.trade;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class OpenTradingMaidScreenS2CPacket {
    public record MaidInfo(int entityId, UUID uuid, Component displayName, String modelId,
                           boolean customNamed, int emeraldValue) {
    }

    private final int traderId;
    private final List<MaidInfo> buyList;
    private final List<MaidInfo> sellList;

    public OpenTradingMaidScreenS2CPacket(int traderId, List<MaidInfo> buyList, List<MaidInfo> sellList) {
        this.traderId = traderId;
        this.buyList = List.copyOf(buyList);
        this.sellList = List.copyOf(sellList);
    }

    public OpenTradingMaidScreenS2CPacket(FriendlyByteBuf buf) {
        traderId = buf.readVarInt();
        buyList = readList(buf);
        sellList = readList(buf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(traderId);
        writeList(buf, buyList);
        writeList(buf, sellList);
    }

    private static List<MaidInfo> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<MaidInfo> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new MaidInfo(buf.readVarInt(), buf.readUUID(), buf.readComponent(),
                    buf.readUtf(256), buf.readBoolean(), buf.readVarInt()));
        }
        return result;
    }

    private static void writeList(FriendlyByteBuf buf, List<MaidInfo> list) {
        buf.writeVarInt(list.size());
        for (MaidInfo info : list) {
            buf.writeVarInt(info.entityId());
            buf.writeUUID(info.uuid());
            buf.writeComponent(info.displayName());
            buf.writeUtf(info.modelId(), 256);
            buf.writeBoolean(info.customNamed());
            buf.writeVarInt(info.emeraldValue());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> TradingMaidTradeScreen.open(traderId, buyList, sellList));
        context.setPacketHandled(true);
    }
}
