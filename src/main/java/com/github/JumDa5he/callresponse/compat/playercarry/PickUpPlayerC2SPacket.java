package com.github.JumDa5he.callresponse.compat.playercarry;

import com.github.JumDa5he.callresponse.compat.menu.PrincessCarryContainer;
import net.minecraft.network.FriendlyByteBuf;
import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.chat.Component;

/** The sender is the only possible passenger. The existing maid-pickup packet is unchanged. */
public record PickUpPlayerC2SPacket(int maidId) implements CustomPacketPayload {
    public static final Type<PickUpPlayerC2SPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "pick_up_player"));
    public static final StreamCodec<FriendlyByteBuf, PickUpPlayerC2SPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> encode(packet, buffer), PickUpPlayerC2SPacket::decode);
    @Override public Type<PickUpPlayerC2SPacket> type() { return TYPE; }

    public static void encode(PickUpPlayerC2SPacket packet, FriendlyByteBuf buffer) { buffer.writeVarInt(packet.maidId); }
    public static PickUpPlayerC2SPacket decode(FriendlyByteBuf buffer) { return new PickUpPlayerC2SPacket(buffer.readVarInt()); }
    public static void handle(PickUpPlayerC2SPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer player)) return;
            boolean started = player.containerMenu instanceof PrincessCarryContainer menu
                    && menu.getMaid() != null && menu.getMaid().getId() == packet.maidId
                    && menu.stillValid(player) && PlayerCarryManager.start(player, menu.getMaid());
            player.displayClientMessage(Component.translatable(started
                    ? "message.callresponse.player_hold.hint" : "message.callresponse.player_hold.failed"), true);
        });
    }
}
