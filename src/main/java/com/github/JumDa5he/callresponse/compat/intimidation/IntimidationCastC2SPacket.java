package com.github.JumDa5he.callresponse.compat.intimidation;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** No client-provided level, radius, targets or cooldown. */
public final class IntimidationCastC2SPacket {
    public IntimidationCastC2SPacket() {
    }

    public IntimidationCastC2SPacket(FriendlyByteBuf ignored) {
    }

    public void encode(FriendlyByteBuf ignored) {
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) IntimidationManager.cast(player);
        });
        context.setPacketHandled(true);
    }
}
