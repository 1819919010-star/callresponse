package com.github.JumDa5he.callresponse.compat.facility;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persists additional facility capacity and the extra seats created for it. */
public final class FacilityCapacitySavedData extends SavedData {
    private static final String DATA_NAME = "callresponse_facility_capacity";
    private static final Codec<FacilityCapacitySavedData> CODEC = CompoundTag.CODEC.xmap(
            FacilityCapacitySavedData::load, FacilityCapacitySavedData::save);
    private static final SavedDataType<FacilityCapacitySavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, DATA_NAME),
            FacilityCapacitySavedData::new, CODEC);

    private final Map<FacilityKey, FacilityRecord> records = new HashMap<>();

    public static FacilityCapacitySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static FacilityCapacitySavedData load(CompoundTag tag) {
        FacilityCapacitySavedData data = new FacilityCapacitySavedData();
        ListTag list = tag.getListOrEmpty("Facilities");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i).orElseGet(CompoundTag::new);
            Identifier dimension = Identifier.tryParse(entry.getStringOr("Dimension", ""));
            if (dimension == null) {
                continue;
            }
            FacilityKey key = new FacilityKey(dimension, entry.getLongOr("Pos", 0L));
            FacilityRecord record = new FacilityRecord(entry.getIntOr("Capacity", 1));
            ListTag seats = entry.getListOrEmpty("Seats");
            for (int j = 0; j < seats.size(); j++) {
                CompoundTag seat = seats.getCompound(j).orElseGet(CompoundTag::new);
                UUID userId = seat.read("User", UUIDUtil.CODEC).orElse(Util.NIL_UUID);
                UUID sitId = seat.read("Sit", UUIDUtil.CODEC).orElse(Util.NIL_UUID);
                if (!Util.NIL_UUID.equals(userId) && !Util.NIL_UUID.equals(sitId)) {
                    record.seats.add(new ExtraSeat(userId, sitId, seat.getIntOr("Order", 0)));
                }
            }
            data.records.put(key, record);
        }
        return data;
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        records.forEach((key, record) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", key.dimension().toString());
            entry.putLong("Pos", key.pos());
            entry.putInt("Capacity", record.capacity);
            ListTag seats = new ListTag();
            for (ExtraSeat seat : record.seats) {
                CompoundTag seatTag = new CompoundTag();
                seatTag.store("User", UUIDUtil.CODEC, seat.userId());
                seatTag.store("Sit", UUIDUtil.CODEC, seat.sitId());
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

    public record FacilityKey(Identifier dimension, long pos) {
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
