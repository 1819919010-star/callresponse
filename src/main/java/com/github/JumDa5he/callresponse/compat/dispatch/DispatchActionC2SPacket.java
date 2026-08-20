package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record DispatchActionC2SPacket(Action action, String eventId, UUID targetId) implements CustomPacketPayload {
    public enum Action { REQUEST, START, RECALL }

    public static final CustomPacketPayload.Type<DispatchActionC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "dispatch_action"));

    public static final StreamCodec<FriendlyByteBuf, DispatchActionC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, DispatchActionC2SPacket pkt) {
            buf.writeEnum(pkt.action);
            buf.writeUtf(pkt.eventId, 256);
            buf.writeUUID(pkt.targetId);
        }

        @Override
        public DispatchActionC2SPacket decode(FriendlyByteBuf buf) {
            return new DispatchActionC2SPacket(buf.readEnum(Action.class), buf.readUtf(256), buf.readUUID());
        }
    };

    public DispatchActionC2SPacket {
        eventId = eventId == null ? "" : eventId;
        targetId = targetId == null ? new UUID(0, 0) : targetId;
    }

    public static void handle(DispatchActionC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                DispatchManager.handle(player, message.action, message.eventId, message.targetId);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
