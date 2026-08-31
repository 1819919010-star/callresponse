package com.github.JumDa5he.callresponse.compat.trade;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record TradingMaidActionC2SPacket(int traderId, UUID maidId, Action action) implements CustomPacketPayload {
    public enum Action { BUY, SELL }

    public static final CustomPacketPayload.Type<TradingMaidActionC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "trading_maid_action"));

    public static final StreamCodec<FriendlyByteBuf, TradingMaidActionC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, TradingMaidActionC2SPacket pkt) {
            buf.writeVarInt(pkt.traderId);
            buf.writeUUID(pkt.maidId);
            buf.writeEnum(pkt.action);
        }

        @Override
        public TradingMaidActionC2SPacket decode(FriendlyByteBuf buf) {
            return new TradingMaidActionC2SPacket(buf.readVarInt(), buf.readUUID(), buf.readEnum(Action.class));
        }
    };

    public static void handle(TradingMaidActionC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                TradingMaidManager.handleTradingAction(player, message.traderId, message.maidId, message.action);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
