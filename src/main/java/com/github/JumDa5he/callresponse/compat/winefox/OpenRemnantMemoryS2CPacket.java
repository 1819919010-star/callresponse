package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.compat.winefox.client.RemnantMemoryScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Only indexes travel over the network; each client uses its complete selected language text. */
public record OpenRemnantMemoryS2CPacket(int first, int second) {
    public boolean valid() {
        return first >= 0 && first < RemnantMemoryItem.MEMORY_COUNT
                && second >= 0 && second < RemnantMemoryItem.MEMORY_COUNT && first != second;
    }
    public static void encode(OpenRemnantMemoryS2CPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.first);
        buffer.writeVarInt(message.second);
    }
    public static OpenRemnantMemoryS2CPacket decode(FriendlyByteBuf buffer) {
        return new OpenRemnantMemoryS2CPacket(buffer.readVarInt(), buffer.readVarInt());
    }
    public static void handle(OpenRemnantMemoryS2CPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        if (message.valid()) context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> RemnantMemoryScreen.open(message.first, message.second)));
        context.setPacketHandled(true);
    }
}
