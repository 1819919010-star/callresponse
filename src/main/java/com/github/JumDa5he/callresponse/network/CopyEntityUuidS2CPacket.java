package com.github.JumDa5he.callresponse.network;

import com.github.JumDa5he.callresponse.CallResponseMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * S2C：把目标的 UUID 复制到玩家剪贴板（shift+右键生物/玩家时发送）。
 * 玩家在狩猎令 GUI 里粘贴添加。
 */
public record CopyEntityUuidS2CPacket(String uuid, String entityName) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CopyEntityUuidS2CPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "copy_entity_uuid"));

    public static final StreamCodec<ByteBuf, CopyEntityUuidS2CPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(ByteBuf buf, CopyEntityUuidS2CPacket pkt) {
            writeUtf(buf, pkt.uuid);
            writeUtf(buf, pkt.entityName);
        }

        @Override
        public CopyEntityUuidS2CPacket decode(ByteBuf buf) {
            return new CopyEntityUuidS2CPacket(
                    readUtf(buf),
                    readUtf(buf));
        }
    };

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

    public static void handle(CopyEntityUuidS2CPacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(message.uuid);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.literal("§a[狩猎令] 已复制 ")
                                .append(Component.literal(message.entityName))
                                .append(Component.literal(" 的 UUID 到剪贴板，可在狩猎令名单中粘贴添加")),
                        false);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
