package com.github.JumDa5he.callresponse.compat.playercarry;

import net.minecraft.network.FriendlyByteBuf;
import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

public record PlayerCarrySyncS2CPacket(UUID playerId, UUID holderId, long started, Vec3 offset, boolean ysmAnimation) implements CustomPacketPayload {
    public static final Type<PlayerCarrySyncS2CPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "player_carry_sync"));
    public static final StreamCodec<FriendlyByteBuf, PlayerCarrySyncS2CPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> encode(packet, buffer), PlayerCarrySyncS2CPacket::decode);
    @Override public Type<PlayerCarrySyncS2CPacket> type() { return TYPE; }

    public static void encode(PlayerCarrySyncS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId);
        buffer.writeBoolean(packet.holderId != null);
        if (packet.holderId != null) {
            buffer.writeUUID(packet.holderId); buffer.writeLong(packet.started);
            buffer.writeDouble(packet.offset.x); buffer.writeDouble(packet.offset.y); buffer.writeDouble(packet.offset.z);
            buffer.writeBoolean(packet.ysmAnimation);
        }
    }
    public static PlayerCarrySyncS2CPacket decode(FriendlyByteBuf buffer) {
        UUID player = buffer.readUUID();
        if (!buffer.readBoolean()) return new PlayerCarrySyncS2CPacket(player, null, 0, Vec3.ZERO, false);
        return new PlayerCarrySyncS2CPacket(player, buffer.readUUID(), buffer.readLong(),
                new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readBoolean());
    }
}
