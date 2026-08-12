package com.github.JumDa5he.callresponse.compat.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class OpenWanderingMaidRequestS2CPacket {
    private final int entityId;
    private final UUID maidId;
    private final Component maidName;

    public OpenWanderingMaidRequestS2CPacket(int entityId, UUID maidId, Component maidName) {
        this.entityId = entityId;
        this.maidId = maidId;
        this.maidName = maidName;
    }

    public OpenWanderingMaidRequestS2CPacket(FriendlyByteBuf buf) {
        entityId = buf.readVarInt();
        maidId = buf.readUUID();
        maidName = buf.readComponent();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUUID(maidId);
        buf.writeComponent(maidName);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> WanderingMaidRequestScreen.open(entityId, maidId, maidName));
        context.setPacketHandled(true);
    }
}
