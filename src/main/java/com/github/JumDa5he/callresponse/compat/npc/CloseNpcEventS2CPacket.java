package com.github.JumDa5he.callresponse.compat.npc;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** 只关闭被威压结算的同一次 NPC 事件窗口。 */
public record CloseNpcEventS2CPacket(UUID maidId, String eventId,
                                     long eventTime) implements CustomPacketPayload {
    public static final Type<CloseNpcEventS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "close_npc_event"));
    public static final StreamCodec<FriendlyByteBuf, CloseNpcEventS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buffer, CloseNpcEventS2CPacket packet) {
            buffer.writeUUID(packet.maidId);
            buffer.writeUtf(packet.eventId);
            buffer.writeVarLong(packet.eventTime);
        }

        @Override
        public CloseNpcEventS2CPacket decode(FriendlyByteBuf buffer) {
            return new CloseNpcEventS2CPacket(buffer.readUUID(), buffer.readUtf(), buffer.readVarLong());
        }
    };

    public static void handle(CloseNpcEventS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> NpcEventScreen.closeIfMatches(
                packet.maidId, packet.eventId, packet.eventTime));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
