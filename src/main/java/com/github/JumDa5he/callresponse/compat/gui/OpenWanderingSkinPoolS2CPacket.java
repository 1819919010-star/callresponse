package com.github.JumDa5he.callresponse.compat.gui;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public final class OpenWanderingSkinPoolS2CPacket {
    private final List<String> selectedModels;

    public OpenWanderingSkinPoolS2CPacket(Collection<String> selectedModels) {
        this.selectedModels = List.copyOf(selectedModels);
    }

    public OpenWanderingSkinPoolS2CPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > 512) {
            throw new IllegalArgumentException("Invalid wandering maid skin pool size: " + size);
        }
        List<String> models = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            models.add(buf.readUtf(256));
        }
        selectedModels = List.copyOf(models);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(selectedModels.size());
        selectedModels.forEach(id -> buf.writeUtf(id, 256));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> WanderingMaidSkinPoolScreen.open(selectedModels));
        context.setPacketHandled(true);
    }
}
