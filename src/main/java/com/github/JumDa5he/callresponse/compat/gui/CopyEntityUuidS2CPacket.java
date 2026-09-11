package com.github.JumDa5he.callresponse.compat.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C：把目标的 UUID 复制到玩家剪贴板（shift+右键生物/玩家时发送）。
 * 玩家在狩猎令 GUI 里粘贴添加。
 */
public class CopyEntityUuidS2CPacket {
    private final String uuid;
    private final String entityName;

    public CopyEntityUuidS2CPacket(String uuid, String entityName) {
        this.uuid = uuid;
        this.entityName = entityName;
    }

    public CopyEntityUuidS2CPacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUtf(64);
        this.entityName = buf.readUtf(64);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(uuid, 64);
        buf.writeUtf(entityName, 64);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(uuid);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("message.callresponse.hunt.copy_prefix")
                                .append(Component.literal(entityName))
                                .append(Component.translatable("message.callresponse.hunt.copy_suffix")),
                        false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}