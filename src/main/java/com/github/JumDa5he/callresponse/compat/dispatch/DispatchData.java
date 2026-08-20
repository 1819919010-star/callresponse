package com.github.JumDa5he.callresponse.compat.dispatch;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

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
    private final Map<UUID, List<DispatchRecord>> records = new HashMap<>();
    private final Map<UUID, EventPool> pools = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, List<BoxRef>> boxes = new HashMap<>();

    public static DispatchData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(DispatchData::load, DispatchData::new, DATA_NAME);
    }

    public static DispatchData load(CompoundTag tag) {
        DispatchData data = new DispatchData();
        ListTag records = tag.getList("Records", Tag.TAG_COMPOUND);
        for (int i = 0; i < records.size(); i++) {
            DispatchRecord record = DispatchRecord.load(records.getCompound(i));
            data.records.computeIfAbsent(record.ownerId(), ignored -> new ArrayList<>()).add(record);
        }
        ListTag pools = tag.getList("Pools", Tag.TAG_COMPOUND);
        for (int i = 0; i < pools.size(); i++) {
            EventPool pool = EventPool.load(pools.getCompound(i));
            data.pools.put(pool.ownerId(), pool);
        }
        ListTag cooldowns = tag.getList("Cooldowns", Tag.TAG_COMPOUND);
        for (int i = 0; i < cooldowns.size(); i++) {
            CompoundTag entry = cooldowns.getCompound(i);
            data.cooldowns.put(entry.getString("Key"), entry.getLong("Until"));
        }
        ListTag boxes = tag.getList("Boxes", Tag.TAG_COMPOUND);
        for (int i = 0; i < boxes.size(); i++) {
            BoxRef box = BoxRef.load(boxes.getCompound(i));
            data.boxes.computeIfAbsent(box.ownerId(), ignored -> new ArrayList<>()).add(box);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
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

    public Set<UUID> owners() { return Set.copyOf(records.keySet()); }

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
        if (list == null) return Optional.empty();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).dispatchId().equals(dispatchId)) {
                DispatchRecord removed = list.remove(i);
                if (list.isEmpty()) records.remove(owner);
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
            tag.putUUID("Owner", ownerId);
            tag.putLong("RefreshAt", refreshAt);
            ListTag workTag = new ListTag(); work.forEach(id -> workTag.add(StringTag.valueOf(id)));
            ListTag playTag = new ListTag(); play.forEach(id -> playTag.add(StringTag.valueOf(id)));
            tag.put("Work", workTag); tag.put("Play", playTag);
            return tag;
        }

        static EventPool load(CompoundTag tag) {
            return new EventPool(tag.getUUID("Owner"), tag.getLong("RefreshAt"), strings(tag, "Work"), strings(tag, "Play"));
        }

        private static List<String> strings(CompoundTag tag, String key) {
            ListTag list = tag.getList(key, Tag.TAG_STRING);
            List<String> result = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) result.add(list.getString(i));
            return List.copyOf(result);
        }
    }

    public record BoxRef(UUID ownerId, String dimension, BlockPos pos) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Owner", ownerId); tag.putString("Dimension", dimension); tag.putLong("Pos", pos.asLong());
            return tag;
        }
        static BoxRef load(CompoundTag tag) {
            return new BoxRef(tag.getUUID("Owner"), tag.getString("Dimension"), BlockPos.of(tag.getLong("Pos")));
        }
    }

    public record DispatchRecord(UUID dispatchId, UUID ownerId, UUID maidId, CompoundTag maidNbt,
                                 String eventId, String eventTitle, String eventDescription, String category,
                                 long startAt, long finishAt, String originDimension, BlockPos originPos,
                                 String boxDimension, BlockPos boxPos, int trust, int fear, int favor, int hunger,
                                 List<ItemStack> rewards, String modelId, String displayName) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Dispatch", dispatchId); tag.putUUID("Owner", ownerId); tag.putUUID("Maid", maidId);
            tag.put("MaidNbt", maidNbt.copy()); tag.putString("Event", eventId); tag.putString("Title", eventTitle);
            tag.putString("Description", eventDescription); tag.putString("Category", category);
            tag.putLong("StartAt", startAt); tag.putLong("FinishAt", finishAt);
            tag.putString("OriginDimension", originDimension); tag.putLong("OriginPos", originPos.asLong());
            tag.putString("BoxDimension", boxDimension == null ? "" : boxDimension);
            tag.putLong("BoxPos", boxPos == null ? 0L : boxPos.asLong());
            tag.putInt("Trust", trust); tag.putInt("Fear", fear); tag.putInt("Favor", favor); tag.putInt("Hunger", hunger);
            ListTag rewardTag = new ListTag(); rewards.forEach(stack -> rewardTag.add(stack.save(new CompoundTag())));
            tag.put("Rewards", rewardTag); tag.putString("ModelId", modelId); tag.putString("DisplayName", displayName);
            return tag;
        }

        public static DispatchRecord load(CompoundTag tag) {
            List<ItemStack> rewards = new ArrayList<>();
            ListTag list = tag.getList("Rewards", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) rewards.add(ItemStack.of(list.getCompound(i)));
            String boxDimension = tag.getString("BoxDimension");
            BlockPos boxPos = boxDimension.isEmpty() ? null : BlockPos.of(tag.getLong("BoxPos"));
            return new DispatchRecord(tag.getUUID("Dispatch"), tag.getUUID("Owner"), tag.getUUID("Maid"),
                    tag.getCompound("MaidNbt"), tag.getString("Event"), tag.getString("Title"),
                    tag.getString("Description"), tag.getString("Category"), tag.getLong("StartAt"), tag.getLong("FinishAt"),
                    tag.getString("OriginDimension"), BlockPos.of(tag.getLong("OriginPos")), boxDimension, boxPos,
                    tag.getInt("Trust"), tag.getInt("Fear"), tag.getInt("Favor"), tag.getInt("Hunger"),
                    List.copyOf(rewards), tag.getString("ModelId"), tag.getString("DisplayName"));
        }
    }
}
