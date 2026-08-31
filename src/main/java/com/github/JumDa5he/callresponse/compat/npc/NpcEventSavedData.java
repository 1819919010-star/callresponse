package com.github.JumDa5he.callresponse.compat.npc;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
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

/**
 * NPC 事件的世界级独立存档。
 * 数据不再写进女仆实体 NBT，因此卸载附属后不会给 TLM 女仆留下行为状态。
 */
public final class NpcEventSavedData extends SavedData {
    private static final String DATA_NAME = "callresponse_npc_events";
    private static final String ENTRIES = "Entries";
    private static final String MAID_UUID = "MaidUUID";
    private static final String EVENT_DATA = "EventData";
    private static final Codec<NpcEventSavedData> CODEC = CompoundTag.CODEC.xmap(
            NpcEventSavedData::load, NpcEventSavedData::save);
    private static final SavedDataType<NpcEventSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, DATA_NAME), NpcEventSavedData::new, CODEC);

    private final Map<UUID, CompoundTag> entries = new HashMap<>();

    public static NpcEventSavedData get(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            throw new IllegalStateException("NPC 事件数据只能在服务端读取");
        }
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static NpcEventSavedData load(CompoundTag tag) {
        NpcEventSavedData result = new NpcEventSavedData();
        ListTag list = tag.getListOrEmpty(ENTRIES);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i).orElseGet(CompoundTag::new);
            UUID maidId = entry.read(MAID_UUID, UUIDUtil.CODEC).orElse(Util.NIL_UUID);
            if (!Util.NIL_UUID.equals(maidId) && entry.contains(EVENT_DATA)) {
                result.entries.put(maidId, entry.getCompoundOrEmpty(EVENT_DATA).copy());
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

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        entries.forEach((maidId, data) -> {
            if (data.isEmpty()) return;
            CompoundTag entry = new CompoundTag();
            entry.store(MAID_UUID, UUIDUtil.CODEC, maidId);
            entry.put(EVENT_DATA, data.copy());
            list.add(entry);
        });
        tag.put(ENTRIES, list);
        return tag;
    }
}
