package com.github.JumDa5he.callresponse.compat.talk;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persists the last day on which each owner started a talk event. */
public final class TalkEventSavedData extends SavedData {
    private static final String DATA_NAME = "callresponse_talk_event";
    private static final Codec<TalkEventSavedData> CODEC = CompoundTag.CODEC.xmap(
            TalkEventSavedData::load, TalkEventSavedData::save);
    private static final SavedDataType<TalkEventSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, DATA_NAME), TalkEventSavedData::new, CODEC);
    private final Map<UUID, Long> lastStartedDay = new HashMap<>();

    public static TalkEventSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    private static TalkEventSavedData load(CompoundTag tag) {
        TalkEventSavedData data = new TalkEventSavedData();
        ListTag entries = tag.getListOrEmpty("Owners");
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i).orElseGet(CompoundTag::new);
            UUID owner = entry.read("Owner", UUIDUtil.CODEC).orElse(Util.NIL_UUID);
            if (!Util.NIL_UUID.equals(owner)) {
                data.lastStartedDay.put(owner, entry.getLongOr("Day", 0L));
            }
        }
        return data;
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag entries = new ListTag();
        lastStartedDay.forEach((owner, day) -> {
            CompoundTag entry = new CompoundTag();
            entry.store("Owner", UUIDUtil.CODEC, owner);
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

    public boolean clear(UUID owner) {
        boolean removed = lastStartedDay.remove(owner) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }
}
