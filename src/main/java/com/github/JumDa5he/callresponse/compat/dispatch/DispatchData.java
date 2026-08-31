package com.github.JumDa5he.callresponse.compat.dispatch;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.Util;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 派遣系统的存档内持久化数据。所有时间均为现实时间戳。 */
public final class DispatchData extends SavedData {
    private static final String DATA_NAME = "callresponse_dispatch";
    private static final Codec<DispatchData> CODEC = CompoundTag.CODEC.xmap(DispatchData::load, DispatchData::save);
    private static final SavedDataType<DispatchData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, DATA_NAME), DispatchData::new, CODEC);
    private final Map<UUID, List<DispatchRecord>> records = new HashMap<>();
    private final Map<UUID, EventPool> pools = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, List<BoxRef>> boxes = new HashMap<>();

    public static DispatchData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public static DispatchData load(CompoundTag tag) {
        DispatchData data = new DispatchData();
        ListTag records = tag.getListOrEmpty("Records");
        for (int i = 0; i < records.size(); i++) {
            DispatchRecord record = DispatchRecord.load(records.getCompound(i).orElseGet(CompoundTag::new));
            data.records.computeIfAbsent(record.ownerId(), ignored -> new ArrayList<>()).add(record);
        }
        ListTag pools = tag.getListOrEmpty("Pools");
        for (int i = 0; i < pools.size(); i++) {
            EventPool pool = EventPool.load(pools.getCompound(i).orElseGet(CompoundTag::new));
            data.pools.put(pool.ownerId(), pool);
        }
        ListTag cooldowns = tag.getListOrEmpty("Cooldowns");
        for (int i = 0; i < cooldowns.size(); i++) {
            CompoundTag entry = cooldowns.getCompound(i).orElseGet(CompoundTag::new);
            data.cooldowns.put(entry.getStringOr("Key", ""), entry.getLongOr("Until", 0L));
        }
        ListTag boxes = tag.getListOrEmpty("Boxes");
        for (int i = 0; i < boxes.size(); i++) {
            BoxRef box = BoxRef.load(boxes.getCompound(i).orElseGet(CompoundTag::new));
            data.boxes.computeIfAbsent(box.ownerId(), ignored -> new ArrayList<>()).add(box);
        }
        return data;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag recordList = new ListTag();
        records.values().forEach(list -> list.forEach(record -> recordList.add(record.save())));
        tag.put("Records", recordList);
        ListTag poolList = new ListTag();
        pools.values().forEach(pool -> poolList.add(pool.save()));
        tag.put("Pools", poolList);
        ListTag cooldownList = new ListTag();
        cooldowns.forEach((key, until) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", key);
            entry.putLong("Until", until);
            cooldownList.add(entry);
        });
        tag.put("Cooldowns", cooldownList);
        ListTag boxList = new ListTag();
        boxes.values().forEach(list -> list.forEach(box -> boxList.add(box.save())));
        tag.put("Boxes", boxList);
        return tag;
    }

    public List<DispatchRecord> active(UUID owner) {
        return List.copyOf(records.getOrDefault(owner, List.of()));
    }

    public Set<UUID> owners() {
        return Set.copyOf(records.keySet());
    }

    public boolean isDispatched(UUID maid) {
        return records.values().stream().flatMap(List::stream).anyMatch(record -> record.maidId().equals(maid));
    }

    public boolean hasDispatch(UUID dispatchId) {
        return records.values().stream().flatMap(List::stream).anyMatch(record -> record.dispatchId().equals(dispatchId));
    }

    public void add(DispatchRecord record) {
        records.computeIfAbsent(record.ownerId(), ignored -> new ArrayList<>()).add(record);
        setDirty();
    }

    public Optional<DispatchRecord> remove(UUID owner, UUID dispatchId) {
        List<DispatchRecord> list = records.get(owner);
        if (list == null) {
            return Optional.empty();
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).dispatchId().equals(dispatchId)) {
                DispatchRecord removed = list.remove(i);
                if (list.isEmpty()) {
                    records.remove(owner);
                }
                setDirty();
                return Optional.of(removed);
            }
        }
        return Optional.empty();
    }

    public EventPool pool(UUID owner) {
        return pools.get(owner);
    }

    public void setPool(EventPool pool) {
        pools.put(pool.ownerId(), pool);
        setDirty();
    }

    public long cooldown(UUID owner, String event) {
        return cooldowns.getOrDefault(owner + ":" + event, 0L);
    }

    public void setCooldown(UUID owner, String event, long until) {
        cooldowns.put(owner + ":" + event, until);
        setDirty();
    }

    public int clearCooldowns(UUID owner) {
        String prefix = owner + ":";
        int before = cooldowns.size();
        cooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        int removed = before - cooldowns.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    public void registerBox(BoxRef box) {
        List<BoxRef> list = boxes.computeIfAbsent(box.ownerId(), ignored -> new ArrayList<>());
        list.removeIf(old -> old.dimension().equals(box.dimension()) && old.pos().equals(box.pos()));
        list.add(box);
        setDirty();
    }

    public void unregisterBox(String dimension, BlockPos pos) {
        boxes.values().forEach(list -> list.removeIf(box -> box.dimension().equals(dimension) && box.pos().equals(pos)));
        setDirty();
    }

    public List<BoxRef> boxes(UUID owner) {
        return List.copyOf(boxes.getOrDefault(owner, List.of()));
    }

    public record EventPool(UUID ownerId, long refreshAt, List<String> work, List<String> play) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.store("Owner", UUIDUtil.CODEC, ownerId);
            tag.putLong("RefreshAt", refreshAt);
            ListTag workTag = new ListTag();
            work.forEach(id -> workTag.add(StringTag.valueOf(id)));
            ListTag playTag = new ListTag();
            play.forEach(id -> playTag.add(StringTag.valueOf(id)));
            tag.put("Work", workTag);
            tag.put("Play", playTag);
            return tag;
        }

        static EventPool load(CompoundTag tag) {
            return new EventPool(tag.read("Owner", UUIDUtil.CODEC).orElse(Util.NIL_UUID),
                    tag.getLongOr("RefreshAt", 0L), strings(tag, "Work"), strings(tag, "Play"));
        }

        private static List<String> strings(CompoundTag tag, String key) {
            ListTag list = tag.getListOrEmpty(key);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                list.getString(i).ifPresent(result::add);
            }
            return List.copyOf(result);
        }
    }

    public record BoxRef(UUID ownerId, String dimension, BlockPos pos) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.store("Owner", UUIDUtil.CODEC, ownerId);
            tag.putString("Dimension", dimension);
            tag.putLong("Pos", pos.asLong());
            return tag;
        }

        static BoxRef load(CompoundTag tag) {
            return new BoxRef(tag.read("Owner", UUIDUtil.CODEC).orElse(Util.NIL_UUID),
                    tag.getStringOr("Dimension", ""), BlockPos.of(tag.getLongOr("Pos", 0L)));
        }
    }

    public record DispatchRecord(UUID dispatchId, UUID ownerId, UUID maidId, CompoundTag maidNbt,
                                 String eventId, Component eventTitle, Component eventDescription, String category,
                                 long startAt, long finishAt, String originDimension, BlockPos originPos,
                                 String boxDimension, BlockPos boxPos, int trust, int fear, int favor, int hunger,
                                 AdvancementRewards rewards, String modelId, Component displayName) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.store("Dispatch", UUIDUtil.CODEC, dispatchId);
            tag.store("Owner", UUIDUtil.CODEC, ownerId);
            tag.store("Maid", UUIDUtil.CODEC, maidId);
            tag.put("MaidNbt", maidNbt.copy());
            tag.putString("Event", eventId);
            putComponent(tag, "Title", eventTitle);
            putComponent(tag, "Description", eventDescription);
            tag.putString("Category", category);
            tag.putLong("StartAt", startAt);
            tag.putLong("FinishAt", finishAt);
            tag.putString("OriginDimension", originDimension);
            tag.putLong("OriginPos", originPos.asLong());
            tag.putString("BoxDimension", boxDimension == null ? "" : boxDimension);
            tag.putLong("BoxPos", boxPos == null ? 0L : boxPos.asLong());
            tag.putInt("Trust", trust);
            tag.putInt("Fear", fear);
            tag.putInt("Favor", favor);
            tag.putInt("Hunger", hunger);
            putAdvancementRewards(tag,"Rewards", rewards);
            tag.putString("ModelId", modelId);
            putComponent(tag, "DisplayName", displayName);
            return tag;
        }

        public static DispatchRecord load(CompoundTag tag) {
            String boxDimension = tag.getStringOr("BoxDimension", "");
            BlockPos boxPos = boxDimension.isEmpty() ? null : BlockPos.of(tag.getLongOr("BoxPos", 0L));
            return new DispatchRecord(tag.read("Dispatch", UUIDUtil.CODEC).orElse(Util.NIL_UUID),
                    tag.read("Owner", UUIDUtil.CODEC).orElse(Util.NIL_UUID),
                    tag.read("Maid", UUIDUtil.CODEC).orElse(Util.NIL_UUID),
                    tag.getCompoundOrEmpty("MaidNbt"), tag.getStringOr("Event", ""), getComponent(tag, "Title"),
                    getComponent(tag, "Description"), tag.getStringOr("Category", "work"),
                    tag.getLongOr("StartAt", 0L), tag.getLongOr("FinishAt", 0L),
                    tag.getStringOr("OriginDimension", ""), BlockPos.of(tag.getLongOr("OriginPos", 0L)), boxDimension, boxPos,
                    tag.getIntOr("Trust", 0), tag.getIntOr("Fear", 0), tag.getIntOr("Favor", 0), tag.getIntOr("Hunger", 0),
                    getAdvancementRewards(tag, "Rewards"), tag.getStringOr("ModelId", ""), getComponent(tag, "DisplayName"));
        }

        public static void putComponent(CompoundTag tag, String key, Component component){
            ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, component)
                    .resultOrPartial(error -> CallResponseMod.LOGGER.error("写入时出现错误：{}", error))
                    .ifPresent(tag1 -> tag.put(key, tag1));
        }

        public static Component getComponent(CompoundTag tag, String key){
            if(!tag.contains(key))return Component.empty();
            return ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, tag.get(key))
                    .resultOrPartial(error -> CallResponseMod.LOGGER.error("读取时出现错误：{}", error))
                    .orElse(Component.empty());
        }

        public static void putAdvancementRewards(CompoundTag tag, String key, AdvancementRewards rewards){
            AdvancementRewards.CODEC.encodeStart(NbtOps.INSTANCE, rewards)
                    .resultOrPartial(error -> CallResponseMod.LOGGER.error("写入时出现错误：{}", error))
                    .ifPresent(tag1 -> tag.put(key, tag1));
        }

        public static AdvancementRewards getAdvancementRewards(CompoundTag tag, String key){
            if(!tag.contains(key))return AdvancementRewards.EMPTY;
            return AdvancementRewards.CODEC.parse(NbtOps.INSTANCE, tag.get(key))
                    .resultOrPartial(error -> CallResponseMod.LOGGER.error("读取时出现错误：{}", error))
                    .orElse(AdvancementRewards.EMPTY);
        }
    }
}
