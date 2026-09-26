package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.disguise.client.ClientDisguiseState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record DisguiseSyncPacket(UUID playerId, DisguiseAppearance appearance,
                                 int remainingTicks, int recognitionTicks) implements CustomPacketPayload {
    public static final Type<DisguiseSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "disguise_sync"));

    public static final StreamCodec<FriendlyByteBuf, DisguiseSyncPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buffer, DisguiseSyncPacket packet) {
            buffer.writeUUID(packet.playerId);
            buffer.writeBoolean(packet.appearance != null);
            if (packet.appearance != null) packet.appearance.write(buffer);
            buffer.writeVarInt(packet.remainingTicks);
            buffer.writeVarInt(packet.recognitionTicks);
        }

        @Override
        public DisguiseSyncPacket decode(FriendlyByteBuf buffer) {
            UUID playerId = buffer.readUUID();
            DisguiseAppearance appearance = buffer.readBoolean() ? DisguiseAppearance.read(buffer) : null;
            return new DisguiseSyncPacket(playerId, appearance, buffer.readVarInt(), buffer.readVarInt());
        }
    };

    public static void handle(DisguiseSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientDisguiseState.accept(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
