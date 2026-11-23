package com.github.JumDa5he.callresponse.compat.winefox;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persisted active-area locks. Missing or unloaded entities never release a lock. */
public final class WineFoxPursuitData extends SavedData {
    private final Map<UUID, Event> active = new HashMap<>();
    public record Event(String dimension, BlockPos respawn, UUID player, boolean pending) {}

    public static WineFoxPursuitData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WineFoxPursuitData::new, (tag, registries) -> WineFoxPursuitData.load(tag)), "callresponse_wine_fox_pursuits");
    }

    public boolean isOccupied(String dimension, BlockPos respawn) {
        // One event per dimension. Changing a personal respawn cannot evade an already live lock.
        return active.values().stream().anyMatch(event -> event.dimension.equals(dimension));
    }

    public void record(UUID leader, String dimension, BlockPos respawn, UUID player) {
        Event event = new Event(dimension, respawn.immutable(), player, false);
        if (active.putIfAbsent(leader, event) == null) setDirty();
    }

    public boolean reserve(UUID leader, String dimension, BlockPos spawn, UUID player) {
        if (isOccupied(dimension, spawn)) return false;
        active.put(leader, new Event(dimension, spawn.immutable(), player, true));
        setDirty();
        return true;
    }

    public boolean isCommitted(UUID leader) {
        Event event = active.get(leader);
        return event != null && !event.pending;
    }

    public void commit(UUID leader) {
        Event event = active.get(leader);
        if (event != null && event.pending) {
            active.put(leader, new Event(event.dimension, event.respawn, event.player, false));
            setDirty();
        }
    }

    public void releaseConfirmedDead(WineFoxLeaders deaths) {
        if (active.keySet().removeIf(deaths::isDead)) setDirty();
    }

    /** Only confirmed death or the same spawn transaction's rollback calls this. */
    public void release(UUID leader) {
        if (active.remove(leader) != null) setDirty();
    }

    public static WineFoxPursuitData load(CompoundTag tag) {
        WineFoxPursuitData data = new WineFoxPursuitData();
        ListTag events = tag.getList("Active", Tag.TAG_COMPOUND);
        for (int i = 0; i < events.size(); i++) {
            CompoundTag entry = events.getCompound(i);
            if (!entry.hasUUID("Leader") || !entry.hasUUID("Player") || !entry.contains("Respawn")
                    || net.minecraft.resources.ResourceLocation.tryParse(entry.getString("Dimension")) == null) continue;
            // Interrupted preparations have no committed leader. Their tagged temporary members
            // are discarded by the existing pursuit join handler, not treated as combat deaths.
            if (entry.getBoolean("Pending")) continue;
            data.active.putIfAbsent(entry.getUUID("Leader"), new Event(entry.getString("Dimension"),
                    BlockPos.of(entry.getLong("Respawn")), entry.getUUID("Player"), false));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag events = new ListTag();
        active.forEach((leader, event) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Leader", leader);
            entry.putUUID("Player", event.player);
            entry.putString("Dimension", event.dimension);
            entry.putLong("Respawn", event.respawn.asLong());
            if (event.pending) entry.putBoolean("Pending", true);
            events.add(entry);
        });
        tag.put("Active", events);
        return tag;
    }
}
