package com.github.JumDa5he.callresponse.network;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.client.gui.WanderingMaidRequestScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record OpenWanderingMaidRequestS2CPacket(int entityId, UUID maidId, Component maidName) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenWanderingMaidRequestS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "open_wandering_maid_request"));

    public static final StreamCodec<FriendlyByteBuf, OpenWanderingMaidRequestS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, OpenWanderingMaidRequestS2CPacket pkt) {
            buf.writeVarInt(pkt.entityId);
            buf.writeUUID(pkt.maidId);
            buf.writeUtf(Component.Serializer.toJson(pkt.maidName, RegistryAccess.EMPTY));
        }

        @Override
        public OpenWanderingMaidRequestS2CPacket decode(FriendlyByteBuf buf) {
            return new OpenWanderingMaidRequestS2CPacket(
                    buf.readVarInt(),
                    buf.readUUID(),
                    Component.Serializer.fromJson(buf.readUtf(), RegistryAccess.EMPTY));
        }
    };

    public static void handle(OpenWanderingMaidRequestS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() ->
                WanderingMaidRequestScreen.open(message.entityId, message.maidId, message.maidName));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
