package com.github.JumDa5he.callresponse.compat.trade;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record OpenTradingMaidScreenS2CPacket(int traderId,
                                             List<MaidInfo> buyList,
                                             List<MaidInfo> sellList) implements CustomPacketPayload {
    public record MaidInfo(int entityId, UUID uuid, Component displayName, String modelId,
                           boolean customNamed, int emeraldValue) {
    }

    public OpenTradingMaidScreenS2CPacket(int traderId, List<MaidInfo> buyList, List<MaidInfo> sellList) {
        this.traderId = traderId;
        this.buyList = List.copyOf(buyList);
        this.sellList = List.copyOf(sellList);
    }

    public static final CustomPacketPayload.Type<OpenTradingMaidScreenS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "open_trading_maid_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenTradingMaidScreenS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, OpenTradingMaidScreenS2CPacket pkt) {
            buf.writeVarInt(pkt.traderId);
            writeList(buf, pkt.buyList);
            writeList(buf, pkt.sellList);
        }

        @Override
        public OpenTradingMaidScreenS2CPacket decode(FriendlyByteBuf buf) {
            int traderId = buf.readVarInt();
            return new OpenTradingMaidScreenS2CPacket(traderId, readList(buf), readList(buf));
        }
    };

    private static List<MaidInfo> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<MaidInfo> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new MaidInfo(buf.readVarInt(), buf.readUUID(),
                    buf.readLenientJsonWithCodec(ComponentSerialization.CODEC),
                    buf.readUtf(256), buf.readBoolean(), buf.readVarInt()));
        }
        return result;
    }

    private static void writeList(FriendlyByteBuf buf, List<MaidInfo> list) {
        buf.writeVarInt(list.size());
        for (MaidInfo info : list) {
            buf.writeVarInt(info.entityId());
            buf.writeUUID(info.uuid());
            buf.writeJsonWithCodec(ComponentSerialization.CODEC, info.displayName());
            buf.writeUtf(info.modelId(), 256);
            buf.writeBoolean(info.customNamed());
            buf.writeVarInt(info.emeraldValue());
        }
    }

    public static void handle(OpenTradingMaidScreenS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> TradingMaidTradeScreen.open(message.traderId, message.buyList, message.sellList));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
