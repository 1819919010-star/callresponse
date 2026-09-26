package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.JumDa5he.callresponse.compat.disguise.client.ClientDisguiseState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record DisguiseSyncPacket(UUID playerId, DisguiseAppearance appearance,
                                 int remainingTicks, int recognitionTicks) {
    public static void encode(DisguiseSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId);
        buffer.writeBoolean(packet.appearance != null);
        if (packet.appearance != null) packet.appearance.write(buffer);
        buffer.writeVarInt(packet.remainingTicks);
        buffer.writeVarInt(packet.recognitionTicks);
    }

    public static DisguiseSyncPacket decode(FriendlyByteBuf buffer) {
        UUID playerId = buffer.readUUID();
        DisguiseAppearance appearance = buffer.readBoolean() ? DisguiseAppearance.read(buffer) : null;
        return new DisguiseSyncPacket(playerId, appearance, buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(DisguiseSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientDisguiseState.accept(packet)));
        context.setPacketHandled(true);
    }
}
