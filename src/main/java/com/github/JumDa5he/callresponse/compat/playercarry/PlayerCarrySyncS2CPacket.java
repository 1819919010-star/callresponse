package com.github.JumDa5he.callresponse.compat.playercarry;

import com.github.JumDa5he.callresponse.compat.playercarry.client.PlayerCarryClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record PlayerCarrySyncS2CPacket(UUID playerId, UUID holderId, long started, Vec3 offset, boolean ysmAnimation) {
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
    public static void handle(PlayerCarrySyncS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PlayerCarryClient.accept(packet)));
        context.setPacketHandled(true);
    }
}
