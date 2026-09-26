package com.github.JumDa5he.callresponse.compat.cuteactivity;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.cuteactivity.client.CuteActivityScareClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record CuteActivityScareS2CPacket(UUID maidId, boolean revengeMaid,
                                         int remainingTicks) implements CustomPacketPayload {
    public static final Type<CuteActivityScareS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "cute_activity_scare"));
    public static final StreamCodec<FriendlyByteBuf, CuteActivityScareS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buffer, CuteActivityScareS2CPacket packet) {
            buffer.writeUUID(packet.maidId);
            buffer.writeBoolean(packet.revengeMaid);
            buffer.writeVarInt(packet.remainingTicks);
        }

        @Override
        public CuteActivityScareS2CPacket decode(FriendlyByteBuf buffer) {
            return new CuteActivityScareS2CPacket(buffer.readUUID(), buffer.readBoolean(), buffer.readVarInt());
        }
    };

    public static void handle(CuteActivityScareS2CPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> CuteActivityScareClient.accept(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
