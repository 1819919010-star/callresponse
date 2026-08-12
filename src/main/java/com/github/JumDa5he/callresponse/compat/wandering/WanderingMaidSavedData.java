package com.github.JumDa5he.callresponse.compat.wandering;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WanderingMaidSavedData extends SavedData {
    public static final String DATA_NAME = "callresponse_wandering_maid";

    private long nextAttemptTick;
    private int spawnChance = 25;
    private final Map<UUID, List<String>> skinPools = new HashMap<>();

    public static WanderingMaidSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WanderingMaidSavedData::new,
                        WanderingMaidSavedData::load,
                        null),
                DATA_NAME);
    }

    public static WanderingMaidSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        WanderingMaidSavedData data = new WanderingMaidSavedData();
        data.nextAttemptTick = tag.getLong("NextAttemptTick");
        data.spawnChance = Math.max(25, Math.min(75, tag.getInt("SpawnChance")));
        ListTag pools = tag.getList("SkinPools", Tag.TAG_COMPOUND);
        for (int i = 0; i < pools.size(); i++) {
            CompoundTag entry = pools.getCompound(i);
            if (!entry.hasUUID("Player")) {
                continue;
            }
            ListTag models = entry.getList("Models", Tag.TAG_STRING);
            List<String> ids = new ArrayList<>();
            for (int j = 0; j < models.size(); j++) {
                ids.add(models.getString(j));
            }
            data.skinPools.put(entry.getUUID("Player"), ids);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("NextAttemptTick", nextAttemptTick);
        tag.putInt("SpawnChance", spawnChance);
        ListTag pools = new ListTag();
        skinPools.forEach((player, models) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            ListTag modelTags = new ListTag();
            models.forEach(id -> modelTags.add(StringTag.valueOf(id)));
            entry.put("Models", modelTags);
            pools.add(entry);
        });
        tag.put("SkinPools", pools);
        return tag;
    }

    public long nextAttemptTick() {
        return nextAttemptTick;
    }

    public void setNextAttemptTick(long value) {
        nextAttemptTick = value;
        setDirty();
    }

    public int spawnChance() {
        return spawnChance;
    }

    public void recordSpawnResult(boolean success) {
        spawnChance = success ? 25 : Math.min(75, spawnChance + 25);
        setDirty();
    }

    public List<String> skinPool(UUID player) {
        return List.copyOf(skinPools.getOrDefault(player, List.of()));
    }

    public void setSkinPool(UUID player, Collection<String> models) {
        Set<String> distinct = new LinkedHashSet<>(models);
        if (distinct.isEmpty()) {
            skinPools.remove(player);
        } else {
            skinPools.put(player, List.copyOf(distinct));
        }
        setDirty();
    }
}
