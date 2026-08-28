package com.github.JumDa5he.callresponse.compat.npc;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record NpcEventChoiceC2SPacket(UUID maidId, int optionIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NpcEventChoiceC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "npc_event_choice"));

    public static final StreamCodec<FriendlyByteBuf, NpcEventChoiceC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, NpcEventChoiceC2SPacket pkt) {
            buf.writeUUID(pkt.maidId);
            buf.writeVarInt(pkt.optionIndex);
        }

        @Override
        public NpcEventChoiceC2SPacket decode(FriendlyByteBuf buf) {
            return new NpcEventChoiceC2SPacket(buf.readUUID(), buf.readVarInt());
        }
    };

    public static void handle(NpcEventChoiceC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                NpcEventManager.handleChoice(player, message.maidId, message.optionIndex);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
