package com.github.JumDa5he.callresponse.compat.npc;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 服务器只发送翻译键，最终显示语言由玩家客户端决定。 */
public final class OpenNpcEventS2CPacket {
    private final int entityId;
    private final UUID maidId;
    private final String eventId;
    private final long eventTime;
    private final String titleKey;
    private final String descriptionKey;
    private final List<String> optionKeys;

    public OpenNpcEventS2CPacket(int entityId, UUID maidId, String eventId, long eventTime, String titleKey,
                                 String descriptionKey, List<String> optionKeys) {
        this.entityId = entityId;
        this.maidId = maidId;
        this.eventId = eventId;
        this.eventTime = eventTime;
        this.titleKey = titleKey;
        this.descriptionKey = descriptionKey;
        this.optionKeys = List.copyOf(optionKeys);
    }

    public OpenNpcEventS2CPacket(FriendlyByteBuf buf) {
        entityId = buf.readVarInt();
        maidId = buf.readUUID();
        eventId = buf.readUtf();
        eventTime = buf.readVarLong();
        titleKey = buf.readUtf();
        descriptionKey = buf.readUtf();
        int size = Math.min(16, Math.max(0, buf.readVarInt()));
        List<String> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) options.add(buf.readUtf());
        optionKeys = List.copyOf(options);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUUID(maidId);
        buf.writeUtf(eventId);
        buf.writeVarLong(eventTime);
        buf.writeUtf(titleKey);
        buf.writeUtf(descriptionKey);
        buf.writeVarInt(optionKeys.size());
        optionKeys.forEach(buf::writeUtf);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> NpcEventScreen.open(entityId, maidId, eventId, eventTime,
                titleKey, descriptionKey, optionKeys));
        context.setPacketHandled(true);
    }
}
