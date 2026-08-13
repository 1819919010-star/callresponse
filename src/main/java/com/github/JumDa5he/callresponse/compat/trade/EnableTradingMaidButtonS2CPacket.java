package com.github.JumDa5he.callresponse.compat.trade;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** Marks the next merchant screen as belonging to a wandering trader. */
public record EnableTradingMaidButtonS2CPacket(int traderId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnableTradingMaidButtonS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "enable_trading_maid_button"));

    public static final StreamCodec<FriendlyByteBuf, EnableTradingMaidButtonS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, EnableTradingMaidButtonS2CPacket pkt) {
            buf.writeVarInt(pkt.traderId);
        }

        @Override
        public EnableTradingMaidButtonS2CPacket decode(FriendlyByteBuf buf) {
            return new EnableTradingMaidButtonS2CPacket(buf.readVarInt());
        }
    };

    public static void handle(EnableTradingMaidButtonS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> TradingMaidClientState.markTrader(message.traderId));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
