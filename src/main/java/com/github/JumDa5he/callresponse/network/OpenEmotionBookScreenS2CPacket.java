package com.github.JumDa5he.callresponse.network;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.client.gui.EmotionBookScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public record OpenEmotionBookScreenS2CPacket(UUID maidUUID, int trust, int fear, int hunger) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenEmotionBookScreenS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "open_emotion_book"));

    public static final StreamCodec<ByteBuf, OpenEmotionBookScreenS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, OpenEmotionBookScreenS2CPacket pkt) {
            buf.writeLong(pkt.maidUUID.getMostSignificantBits());
            buf.writeLong(pkt.maidUUID.getLeastSignificantBits());
            buf.writeInt(pkt.trust);
            buf.writeInt(pkt.fear);
            buf.writeInt(pkt.hunger);
        }
        @Override
        public OpenEmotionBookScreenS2CPacket decode(ByteBuf buf) {
            return new OpenEmotionBookScreenS2CPacket(
                    new UUID(buf.readLong(), buf.readLong()),
                    buf.readInt(), buf.readInt(), buf.readInt());
        }
    };

    public static void handle(OpenEmotionBookScreenS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> EmotionBookScreen.open(message.maidUUID, message.trust, message.fear, message.hunger));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
