package com.github.JumDa5he.callresponse.network;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record WanderingMaidDecisionC2SPacket(UUID maidId, boolean accept) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WanderingMaidDecisionC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "wandering_maid_decision"));

    public static final StreamCodec<FriendlyByteBuf, WanderingMaidDecisionC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, WanderingMaidDecisionC2SPacket pkt) {
            buf.writeUUID(pkt.maidId);
            buf.writeBoolean(pkt.accept);
        }

        @Override
        public WanderingMaidDecisionC2SPacket decode(FriendlyByteBuf buf) {
            return new WanderingMaidDecisionC2SPacket(buf.readUUID(), buf.readBoolean());
        }
    };

    public static void handle(WanderingMaidDecisionC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                WanderingMaidManager.handleDecision(player, message.maidId, message.accept);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
