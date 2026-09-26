package com.github.JumDa5he.callresponse.compat.npc;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NPC 事件的世界级独立存档。
 * 数据不再写进女仆实体 NBT，因此卸载附属后不会给 TLM 女仆留下行为状态。
 */
public final class NpcEventSavedData extends SavedData {
    private static final String DATA_NAME = "callresponse_npc_events";
    private static final String ENTRIES = "Entries";
    private static final String MAID_UUID = "MaidUUID";
    private static final String EVENT_DATA = "EventData";

    private final Map<UUID, CompoundTag> entries = new HashMap<>();

    public static NpcEventSavedData get(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("NPC 事件数据只能在服务端读取");
        }
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(NpcEventSavedData::new, NpcEventSavedData::load, null), DATA_NAME);
    }

    private static NpcEventSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        NpcEventSavedData result = new NpcEventSavedData();
        ListTag list = tag.getList(ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID(MAID_UUID) && entry.contains(EVENT_DATA, Tag.TAG_COMPOUND)) {
                result.entries.put(entry.getUUID(MAID_UUID), entry.getCompound(EVENT_DATA).copy());
            }
        }
        return result;
    }

    public CompoundTag getOrCreate(UUID maidId) {
        return entries.computeIfAbsent(maidId, ignored -> {
            setDirty();
            return new CompoundTag();
        });
    }

    public void replace(UUID maidId, CompoundTag data) {
        entries.put(maidId, data.copy());
        setDirty();
    }

    public void remove(UUID maidId) {
        if (entries.remove(maidId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        entries.forEach((maidId, data) -> {
            if (data.isEmpty()) return;
            CompoundTag entry = new CompoundTag();
            entry.putUUID(MAID_UUID, maidId);
            entry.put(EVENT_DATA, data.copy());
            list.add(entry);
        });
        tag.put(ENTRIES, list);
        return tag;
    }
}
