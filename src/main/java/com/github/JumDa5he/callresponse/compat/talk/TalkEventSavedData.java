package com.github.JumDa5he.callresponse.compat.talk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persists the last day on which each owner started a talk event. */
public final class TalkEventSavedData extends SavedData {
    private static final String DATA_NAME = "callresponse_talk_event";
    private final Map<UUID, Long> lastStartedDay = new HashMap<>();

    public static TalkEventSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(TalkEventSavedData::new,
                        TalkEventSavedData::load,
                        null),
                DATA_NAME);
    }

    public static TalkEventSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        TalkEventSavedData data = new TalkEventSavedData();
        ListTag entries = tag.getList("Owners", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Owner")) {
                data.lastStartedDay.put(entry.getUUID("Owner"), entry.getLong("Day"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        lastStartedDay.forEach((owner, day) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putLong("Day", day);
            entries.add(entry);
        });
        tag.put("Owners", entries);
        return tag;
    }

    public boolean mayStart(UUID owner, long day) {
        return lastStartedDay.getOrDefault(owner, Long.MIN_VALUE) != day;
    }

    public void recordStart(UUID owner, long day) {
        lastStartedDay.put(owner, day);
        setDirty();
    }
}
