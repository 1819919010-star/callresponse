package com.github.JumDa5he.callresponse.compat.disguise;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Raid participation and settlement survive a world save without altering vanilla Raid waves. */
public final class OutpostRaidParticipationSavedData extends SavedData {
    private static final String NAME = "callresponse_outpost_raid_participation";
    private final Map<String, RaidEntry> entries = new HashMap<>();

    public static final class RaidEntry {
        private final String dimension;
        private final int raidId;
        private final String campKey;
        private final Set<UUID> participants = new HashSet<>();
        private boolean closed;

        private RaidEntry(String dimension, int raidId, String campKey) {
            this.dimension = dimension;
            this.raidId = raidId;
            this.campKey = campKey;
        }

        public String dimension() { return dimension; }
        public int raidId() { return raidId; }
        public String campKey() { return campKey; }
        public Set<UUID> participants() { return Set.copyOf(participants); }
        public boolean closed() { return closed; }
    }

    public static OutpostRaidParticipationSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OutpostRaidParticipationSavedData::new,
                        OutpostRaidParticipationSavedData::load, null), NAME);
    }

    private static String key(String dimension, int id) {
        return dimension + "#" + id;
    }

    private static OutpostRaidParticipationSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        OutpostRaidParticipationSavedData data = new OutpostRaidParticipationSavedData();
        ListTag list = root.getList("Raids", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            RaidEntry entry = new RaidEntry(tag.getString("Dimension"), tag.getInt("Id"), tag.getString("Camp"));
            entry.closed = tag.getBoolean("Closed");
            ListTag players = tag.getList("Participants", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < players.size(); j++) {
                CompoundTag wrapper = new CompoundTag();
                wrapper.put("Player", players.get(j));
                if (wrapper.hasUUID("Player")) entry.participants.add(wrapper.getUUID("Player"));
            }
            data.entries.put(key(entry.dimension, entry.raidId), entry);
        }
        return data;
    }

    public void participate(ServerLevel level, int raidId, String campKey, UUID player) {
        String dimension = level.dimension().location().toString();
        RaidEntry entry = entries.computeIfAbsent(key(dimension, raidId),
                ignored -> new RaidEntry(dimension, raidId, campKey));
        if (!entry.closed && entry.campKey.equals(campKey) && entry.participants.add(player)) setDirty();
    }

    public List<RaidEntry> pending(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        List<RaidEntry> pending = new ArrayList<>();
        for (RaidEntry entry : entries.values()) {
            if (!entry.closed && entry.dimension.equals(dimension)) pending.add(entry);
        }
        return pending;
    }

    public void close(RaidEntry entry) {
        if (!entry.closed) {
            entry.closed = true;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (RaidEntry entry : entries.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", entry.dimension);
            tag.putInt("Id", entry.raidId);
            tag.putString("Camp", entry.campKey);
            tag.putBoolean("Closed", entry.closed);
            ListTag players = new ListTag();
            for (UUID player : entry.participants) {
                CompoundTag wrapper = new CompoundTag();
                wrapper.putUUID("Player", player);
                players.add(wrapper.get("Player"));
            }
            tag.put("Participants", players);
            list.add(tag);
        }
        root.put("Raids", list);
        return root;
    }
}
