package com.github.JumDa5he.callresponse.compat.intimidation;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** No client-provided level, radius, targets or cooldown. */
public record IntimidationCastC2SPacket() implements CustomPacketPayload {
    public static final Type<IntimidationCastC2SPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "intimidation_cast"));
    public static final StreamCodec<FriendlyByteBuf, IntimidationCastC2SPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {}, buffer -> new IntimidationCastC2SPacket());

    public static void handle(IntimidationCastC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) IntimidationManager.cast(player);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
