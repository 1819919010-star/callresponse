package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.CallResponseMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S2C：右键狩猎令时，服务端把所有在线死忠女仆的列表发回客户端。
 */
public record MaidListS2CPacket(List<MaidInfo> maids) implements CustomPacketPayload {
    public static class MaidInfo {
        public final UUID uuid;
        public final String typeKey;
        public final String customName;

        public MaidInfo(UUID uuid, String typeKey, String customName) {
            this.uuid = uuid;
            this.typeKey = typeKey;
            this.customName = customName;
        }
    }

    public static final CustomPacketPayload.Type<MaidListS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "maid_list"));

    public static final StreamCodec<ByteBuf, MaidListS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, MaidListS2CPacket pkt) {
            buf.writeInt(pkt.maids.size());
            for (MaidInfo info : pkt.maids) {
                buf.writeLong(info.uuid.getMostSignificantBits());
                buf.writeLong(info.uuid.getLeastSignificantBits());
                writeUtf(buf, info.typeKey);
                buf.writeBoolean(info.customName != null);
                if (info.customName != null) {
                    writeUtf(buf, info.customName);
                }
            }
        }

        @Override
        public MaidListS2CPacket decode(ByteBuf buf) {
            int count = buf.readInt();
            List<MaidInfo> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                UUID uuid = new UUID(buf.readLong(), buf.readLong());
                String typeKey = readUtf(buf);
                boolean hasCustom = buf.readBoolean();
                String customName = hasCustom ? readUtf(buf) : null;
                list.add(new MaidInfo(uuid, typeKey, customName));
            }
            return new MaidListS2CPacket(list);
        }
    };

    public static void handle(MaidListS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> MaidListScreen.open(message.maids));
    }

    private static void writeUtf(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readUtf(ByteBuf buf) {
        int length = buf.readShort();
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
