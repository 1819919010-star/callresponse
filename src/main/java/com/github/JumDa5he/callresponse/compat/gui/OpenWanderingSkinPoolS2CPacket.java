package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record OpenWanderingSkinPoolS2CPacket(List<String> selectedModels) implements CustomPacketPayload {
    public OpenWanderingSkinPoolS2CPacket(Collection<String> selectedModels) {
        this(List.copyOf(selectedModels));
    }

    public static final CustomPacketPayload.Type<OpenWanderingSkinPoolS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "open_wandering_skin_pool"));

    public static final StreamCodec<FriendlyByteBuf, OpenWanderingSkinPoolS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, OpenWanderingSkinPoolS2CPacket pkt) {
            buf.writeVarInt(pkt.selectedModels.size());
            pkt.selectedModels.forEach(id -> buf.writeUtf(id, 256));
        }

        @Override
        public OpenWanderingSkinPoolS2CPacket decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            if (size < 0 || size > 512) {
                throw new IllegalArgumentException("Invalid wandering maid skin pool size: " + size);
            }
            List<String> models = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                models.add(buf.readUtf(256));
            }
            return new OpenWanderingSkinPoolS2CPacket(models);
        }
    };

    public static void handle(OpenWanderingSkinPoolS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> WanderingMaidSkinPoolScreen.open(message.selectedModels));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
