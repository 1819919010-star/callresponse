package com.github.JumDa5he.callresponse.network;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.MaidExpelManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record ExpelMaidC2SPacket(UUID maidId, boolean confirm) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ExpelMaidC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "expel_maid"));

    public static final StreamCodec<FriendlyByteBuf, ExpelMaidC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buffer, ExpelMaidC2SPacket pkt) {
            buffer.writeUUID(pkt.maidId);
            buffer.writeBoolean(pkt.confirm);
        }

        @Override
        public ExpelMaidC2SPacket decode(FriendlyByteBuf buffer) {
            return new ExpelMaidC2SPacket(buffer.readUUID(), buffer.readBoolean());
        }
    };

    public static void handle(ExpelMaidC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                return;
            }
            if (!(player.serverLevel().getEntity(message.maidId) instanceof EntityMaid maid) || !maid.isOwnedBy(player)) {
                return;
            }
            if (message.confirm) {
                MaidExpelManager.expel(player, maid);
            } else {
                MaidExpelManager.frighten(player, maid);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
