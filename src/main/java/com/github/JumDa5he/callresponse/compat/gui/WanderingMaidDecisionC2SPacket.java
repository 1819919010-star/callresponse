package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class WanderingMaidDecisionC2SPacket {
    private final UUID maidId;
    private final boolean accept;

    public WanderingMaidDecisionC2SPacket(UUID maidId, boolean accept) {
        this.maidId = maidId;
        this.accept = accept;
    }

    public WanderingMaidDecisionC2SPacket(FriendlyByteBuf buf) {
        maidId = buf.readUUID();
        accept = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidId);
        buf.writeBoolean(accept);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                WanderingMaidManager.handleDecision(player, maidId, accept);
            }
        });
        context.setPacketHandled(true);
    }
}
