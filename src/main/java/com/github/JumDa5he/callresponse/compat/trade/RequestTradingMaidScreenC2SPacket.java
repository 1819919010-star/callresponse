package com.github.JumDa5he.callresponse.compat.trade;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record RequestTradingMaidScreenC2SPacket(int traderId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestTradingMaidScreenC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "request_trading_maid_screen"));

    public static final StreamCodec<FriendlyByteBuf, RequestTradingMaidScreenC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, RequestTradingMaidScreenC2SPacket pkt) {
            buf.writeVarInt(pkt.traderId);
        }

        @Override
        public RequestTradingMaidScreenC2SPacket decode(FriendlyByteBuf buf) {
            return new RequestTradingMaidScreenC2SPacket(buf.readVarInt());
        }
    };

    public static void handle(RequestTradingMaidScreenC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                TradingMaidManager.sendTradingScreen(player, message.traderId);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
