package com.github.JumDa5he.callresponse.compat.facility;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/** 《呼应》独立保存的设施容量和扩展座位，不向 TLM 方块实体写入额外 NBT。 */
public final class FacilityCapacitySavedData extends SavedData {
    private static final String DATA_NAME = "callresponse_facility_capacity";
    private final Map<FacilityKey, FacilityRecord> records = new HashMap<>();

    public static FacilityCapacitySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FacilityCapacitySavedData::new, FacilityCapacitySavedData::load, null), DATA_NAME);
    }

    public static FacilityCapacitySavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        FacilityCapacitySavedData data = new FacilityCapacitySavedData();
        ListTag list = tag.getList("Facilities", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(entry.getString("Dimension"));
            if (dimension == null) {
                continue;
            }
            FacilityKey key = new FacilityKey(dimension, entry.getLong("Pos"));
            FacilityRecord record = new FacilityRecord(entry.getInt("Capacity"));
            ListTag seats = entry.getList("Seats", Tag.TAG_COMPOUND);
            for (int j = 0; j < seats.size(); j++) {
                CompoundTag seat = seats.getCompound(j);
                if (seat.hasUUID("User") && seat.hasUUID("Sit")) {
                    record.seats.add(new ExtraSeat(seat.getUUID("User"), seat.getUUID("Sit"), seat.getInt("Order")));
                }
            }
            data.records.put(key, record);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        records.forEach((key, record) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", key.dimension().toString());
            entry.putLong("Pos", key.pos());
            entry.putInt("Capacity", record.capacity);
            ListTag seats = new ListTag();
            for (ExtraSeat seat : record.seats) {
                CompoundTag seatTag = new CompoundTag();
                seatTag.putUUID("User", seat.userId());
                seatTag.putUUID("Sit", seat.sitId());
                seatTag.putInt("Order", seat.order());
                seats.add(seatTag);
            }
            entry.put("Seats", seats);
            list.add(entry);
        });
        tag.put("Facilities", list);
        return tag;
    }

    public FacilityRecord get(FacilityKey key) {
        return records.get(key);
    }

    /** 只暴露只读键集合，供床在被 TLM POI 移除后按维度找回容量覆盖位置。 */
    public Set<FacilityKey> keys() {
        return Set.copyOf(records.keySet());
    }

    public FacilityRecord getOrCreate(FacilityKey key, int defaultCapacity) {
        FacilityRecord record = records.computeIfAbsent(key, ignored -> new FacilityRecord(defaultCapacity));
        setDirty();
        return record;
    }

    public void remove(FacilityKey key) {
        if (records.remove(key) != null) {
            setDirty();
        }
    }

    public record FacilityKey(ResourceLocation dimension, long pos) {
    }

    public static final class FacilityRecord {
        private int capacity;
        private final List<ExtraSeat> seats = new ArrayList<>();

        private FacilityRecord(int capacity) {
            this.capacity = Math.max(1, capacity);
        }

        public int capacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = Math.max(1, capacity);
        }

        public List<ExtraSeat> seats() {
            return seats;
        }

    }

    public record ExtraSeat(UUID userId, UUID sitId, int order) {
    }

}
