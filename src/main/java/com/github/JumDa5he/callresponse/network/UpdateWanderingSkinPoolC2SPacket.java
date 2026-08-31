package com.github.JumDa5he.callresponse.network;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidSavedData;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record UpdateWanderingSkinPoolC2SPacket(List<String> selectedModels) implements CustomPacketPayload {
    public UpdateWanderingSkinPoolC2SPacket(Collection<String> selectedModels) {
        this(List.copyOf(selectedModels));
    }

    public static final CustomPacketPayload.Type<UpdateWanderingSkinPoolC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "update_wandering_skin_pool"));

    public static final StreamCodec<FriendlyByteBuf, UpdateWanderingSkinPoolC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, UpdateWanderingSkinPoolC2SPacket pkt) {
            buf.writeVarInt(Math.min(pkt.selectedModels.size(), 512));
            pkt.selectedModels.stream().limit(512).forEach(id -> buf.writeUtf(id, 256));
        }

        @Override
        public UpdateWanderingSkinPoolC2SPacket decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > 512) {
                throw new IllegalArgumentException("Invalid wandering maid skin pool size: " + size);
            }
            List<String> models = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                models.add(buf.readUtf(256));
            }
            return new UpdateWanderingSkinPoolC2SPacket(models);
        }
    };

    public static void handle(UpdateWanderingSkinPoolC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null || player.level().getServer() == null) {
                return;
            }
            Set<String> available = ServerCustomPackLoader.SERVER_MAID_MODELS.getModelIdSet();
            LinkedHashSet<String> validated = new LinkedHashSet<>();
            for (String id : message.selectedModels) {
                if (available.contains(id)) {
                    validated.add(id);
                }
            }
            WanderingMaidSavedData.get(player.level().getServer().overworld())
                    .setSkinPool(player.getUUID(), validated);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
