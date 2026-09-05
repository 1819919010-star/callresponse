package com.github.JumDa5he.callresponse.compat.cage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** 确保同一座原版结构只注入一次铁笼。 */
public final class CageStructureSavedData extends SavedData {
    private static final String DATA_NAME = "callresponse_cage_structures";
    private final Set<String> processedStructures = new HashSet<>();

    public static CageStructureSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CageStructureSavedData::new, CageStructureSavedData::load, null), DATA_NAME);
    }

    private static CageStructureSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        CageStructureSavedData data = new CageStructureSavedData();
        ListTag values = tag.getList("Processed", Tag.TAG_STRING);
        for (int i = 0; i < values.size(); i++) data.processedStructures.add(values.getString(i));
        return data;
    }

    public boolean contains(String key) {
        return processedStructures.contains(key);
    }

    public void markProcessed(String key) {
        if (processedStructures.add(key)) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag values = new ListTag();
        processedStructures.forEach(value -> values.add(StringTag.valueOf(value)));
        tag.put("Processed", values);
        return tag;
    }
}
