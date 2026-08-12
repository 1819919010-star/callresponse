package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidSavedData;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class UpdateWanderingSkinPoolC2SPacket {
    private final List<String> selectedModels;

    public UpdateWanderingSkinPoolC2SPacket(Collection<String> selectedModels) {
        this.selectedModels = List.copyOf(selectedModels);
    }

    public UpdateWanderingSkinPoolC2SPacket(FriendlyByteBuf buf) {
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
        buf.writeVarInt(Math.min(selectedModels.size(), 512));
        selectedModels.stream().limit(512).forEach(id -> buf.writeUtf(id, 256));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.getServer() == null) {
                return;
            }
            Set<String> available = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelIdSet();
            LinkedHashSet<String> validated = new LinkedHashSet<>();
            for (String id : selectedModels) {
                if (available.contains(id)) {
                    validated.add(id);
                }
            }
            WanderingMaidSavedData.get(player.getServer().overworld())
                    .setSkinPool(player.getUUID(), validated);
        });
        context.setPacketHandled(true);
    }
}
