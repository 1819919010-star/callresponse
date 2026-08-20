package com.github.JumDa5he.callresponse.compat.dispatch;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class OpenDispatchScreenS2CPacket {
    public record EventInfo(String id, String category, String title, String description, int durationMin,
                            int durationMax, List<ItemStack> rewards) {}
    public record MaidInfo(UUID id, String name, String modelId) {}
    public record ActiveInfo(UUID dispatchId, String name, String modelId, String title, long finishAt) {}

    private final List<EventInfo> events;
    private final List<MaidInfo> maids;
    private final List<ActiveInfo> active;
    private final int limit;

    public OpenDispatchScreenS2CPacket(List<EventInfo> events, List<MaidInfo> maids, List<ActiveInfo> active, int limit) {
        this.events = List.copyOf(events); this.maids = List.copyOf(maids); this.active = List.copyOf(active); this.limit = limit;
    }
    public OpenDispatchScreenS2CPacket(FriendlyByteBuf buf) {
        limit = buf.readVarInt();
        int eventSize = buf.readVarInt(); List<EventInfo> eventList = new ArrayList<>();
        for (int i = 0; i < eventSize; i++) {
            String id = buf.readUtf(256), category = buf.readUtf(16), title = buf.readUtf(256), description = buf.readUtf(2048);
            int min = buf.readVarInt(), max = buf.readVarInt(), rewardSize = buf.readVarInt();
            List<ItemStack> rewards = new ArrayList<>(); for (int j = 0; j < rewardSize; j++) rewards.add(buf.readItem());
            eventList.add(new EventInfo(id, category, title, description, min, max, List.copyOf(rewards)));
        }
        events = List.copyOf(eventList);
        int maidSize = buf.readVarInt(); List<MaidInfo> maidList = new ArrayList<>();
        for (int i = 0; i < maidSize; i++) maidList.add(new MaidInfo(buf.readUUID(), buf.readUtf(256), buf.readUtf(256)));
        maids = List.copyOf(maidList);
        int activeSize = buf.readVarInt(); List<ActiveInfo> activeList = new ArrayList<>();
        for (int i = 0; i < activeSize; i++) activeList.add(new ActiveInfo(buf.readUUID(), buf.readUtf(256), buf.readUtf(256), buf.readUtf(256), buf.readLong()));
        active = List.copyOf(activeList);
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(limit); buf.writeVarInt(events.size());
        for (EventInfo event : events) {
            buf.writeUtf(event.id(), 256); buf.writeUtf(event.category(), 16); buf.writeUtf(event.title(), 256); buf.writeUtf(event.description(), 2048);
            buf.writeVarInt(event.durationMin()); buf.writeVarInt(event.durationMax()); buf.writeVarInt(event.rewards().size()); event.rewards().forEach(buf::writeItem);
        }
        buf.writeVarInt(maids.size()); for (MaidInfo maid : maids) { buf.writeUUID(maid.id()); buf.writeUtf(maid.name(), 256); buf.writeUtf(maid.modelId(), 256); }
        buf.writeVarInt(active.size()); for (ActiveInfo info : active) { buf.writeUUID(info.dispatchId()); buf.writeUtf(info.name(), 256); buf.writeUtf(info.modelId(), 256); buf.writeUtf(info.title(), 256); buf.writeLong(info.finishAt()); }
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> DispatchScreen.open(events, maids, active, limit)); context.setPacketHandled(true);
    }
}
