package com.github.JumDa5he.callresponse.compat.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.client.CuteActivityScareClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Own sync channel: the third-party ScarePacket and its injury timer remain untouched. */
public record CuteActivityScareS2CPacket(UUID maidId, boolean revengeMaid, int remainingTicks) {
    public static void encode(CuteActivityScareS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.maidId);
        buffer.writeBoolean(packet.revengeMaid);
        buffer.writeVarInt(packet.remainingTicks);
    }

    public static CuteActivityScareS2CPacket decode(FriendlyByteBuf buffer) {
        return new CuteActivityScareS2CPacket(buffer.readUUID(), buffer.readBoolean(), buffer.readVarInt());
    }

    public static void handle(CuteActivityScareS2CPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> CuteActivityScareClient.accept(packet)));
        context.setPacketHandled(true);
    }
}
