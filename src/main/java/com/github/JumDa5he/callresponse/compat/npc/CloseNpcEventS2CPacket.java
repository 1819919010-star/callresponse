package com.github.JumDa5he.callresponse.compat.npc;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Closes only the exact NPC event instance consumed on the server. */
public final class CloseNpcEventS2CPacket {
    private final UUID maidId;
    private final String eventId;
    private final long eventTime;

    public CloseNpcEventS2CPacket(UUID maidId, String eventId, long eventTime) {
        this.maidId = maidId;
        this.eventId = eventId;
        this.eventTime = eventTime;
    }

    public CloseNpcEventS2CPacket(FriendlyByteBuf buf) {
        maidId = buf.readUUID();
        eventId = buf.readUtf();
        eventTime = buf.readVarLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidId);
        buf.writeUtf(eventId);
        buf.writeVarLong(eventTime);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> NpcEventScreen.closeIfMatches(maidId, eventId, eventTime));
        context.setPacketHandled(true);
    }
}
