package com.github.JumDa5he.callresponse.compat.gui;

import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class OpenHuntOrderScreenS2CPacket {
    private final UUID maidUUID;
    private final List<HuntOrderEntry> entries;

    public OpenHuntOrderScreenS2CPacket(UUID maidUUID, List<HuntOrderEntry> entries) {
        this.maidUUID = maidUUID;
        this.entries = new ArrayList<>(entries);
    }

    public OpenHuntOrderScreenS2CPacket(FriendlyByteBuf buf) {
        this.maidUUID = buf.readUUID();
        int count = buf.readVarInt();
        this.entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new HuntOrderEntry(buf.readUUID(), buf.readUtf(64), buf.readBoolean()));
        }
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(maidUUID);
        buf.writeVarInt(entries.size());
        for (HuntOrderEntry entry : entries) {
            buf.writeUUID(entry.uuid);
            buf.writeUtf(entry.name, 64);
            buf.writeBoolean(entry.isPlayer);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> HuntOrderScreen.refresh(maidUUID, entries));
        ctx.get().setPacketHandled(true);
    }
}