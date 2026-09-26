package com.github.JumDa5he.callresponse.compat.npc;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public final class NpcEventChoiceC2SPacket {
    private final UUID maidId;
    private final String eventId;
    private final long eventTime;
    private final int optionIndex;

    public NpcEventChoiceC2SPacket(UUID maidId, String eventId, long eventTime, int optionIndex) {
        this.maidId = maidId;
        this.eventId = eventId;
        this.eventTime = eventTime;
        this.optionIndex = optionIndex;
    }

    public NpcEventChoiceC2SPacket(FriendlyByteBuf buf) {
        maidId = buf.readUUID();
        eventId = buf.readUtf();
        eventTime = buf.readVarLong();
        optionIndex = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidId);
        buf.writeUtf(eventId);
        buf.writeVarLong(eventTime);
        buf.writeVarInt(optionIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) NpcEventManager.handleChoice(player, maidId, eventId, eventTime, optionIndex);
        });
        context.setPacketHandled(true);
    }
}
