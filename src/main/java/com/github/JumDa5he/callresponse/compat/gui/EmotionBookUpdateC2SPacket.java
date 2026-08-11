package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record EmotionBookUpdateC2SPacket(UUID maidUUID, int trust, int fear, int hunger) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EmotionBookUpdateC2SPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "emotion_book_update"));

    public static final StreamCodec<ByteBuf, EmotionBookUpdateC2SPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, EmotionBookUpdateC2SPacket pkt) {
            buf.writeLong(pkt.maidUUID.getMostSignificantBits());
            buf.writeLong(pkt.maidUUID.getLeastSignificantBits());
            buf.writeInt(pkt.trust);
            buf.writeInt(pkt.fear);
            buf.writeInt(pkt.hunger);
        }
        @Override
        public EmotionBookUpdateC2SPacket decode(ByteBuf buf) {
            return new EmotionBookUpdateC2SPacket(
                    new UUID(buf.readLong(), buf.readLong()),
                    buf.readInt(), buf.readInt(), buf.readInt());
        }
    };

    public static void handle(EmotionBookUpdateC2SPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var level = player.serverLevel();
            var entity = level.getEntity(message.maidUUID);
            if (!(entity instanceof EntityMaid maid)) return;
            if (!maid.isTame() || !player.getUUID().equals(maid.getOwnerUUID())) return;
            EmotionData.set(maid, player.getUUID(),
                    Math.clamp(message.trust, 0, 100),
                    Math.clamp(message.fear, 0, 100));
            HungerData.set(maid, Math.clamp(message.hunger, 0, 100));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
