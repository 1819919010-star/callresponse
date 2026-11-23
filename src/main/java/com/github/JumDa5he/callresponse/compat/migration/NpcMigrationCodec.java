package com.github.JumDa5he.callresponse.compat.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Loader-neutral v1 wire format. Identical NPC keys in Forge 1.20.1 and NeoForge 1.21.1. */
public final class NpcMigrationCodec {
    private NpcMigrationCodec() {}
    private static final String[] ELAPSED = {"LastRandomCheck", "LastDreamCheck", "LastOwnerInteraction"};
    private static final String[] DAYS = {"LastDay", "LastDreamEventDay"};

    public static CompoundTag exportData(CompoundTag existing, long now, long day) {
        if (existing == null || existing.isEmpty()) return null;
        CompoundTag wire = new CompoundTag();
        wire.putInt("SchemaVersion", 1);
        CompoundTag state = new CompoundTag();
        for (String key : new String[]{"WorkTicks", "MealCount", "FoodTypes"}) {
            if (existing.contains(key)) state.put(key, existing.get(key).copy());
        }
        if (existing.contains("Cooldowns", Tag.TAG_COMPOUND)) {
            CompoundTag durations = new CompoundTag();
            CompoundTag old = existing.getCompound("Cooldowns");
            for (String id : old.getAllKeys()) {
                if (!old.contains(id, Tag.TAG_LONG)) throw new IllegalArgumentException("NPC Cooldowns/" + id);
                durations.putLong(id, Math.max(0, old.getLong(id) - now));
            }
            state.put("CooldownRemaining", durations);
        }
        for (String key : ELAPSED) if (existing.contains(key, Tag.TAG_LONG)) {
            state.putLong(key + "Elapsed", Math.max(0, now - existing.getLong(key)));
        }
        for (String key : DAYS) if (existing.contains(key, Tag.TAG_LONG)) {
            state.putLong(key + "Offset", existing.getLong(key) - day);
        }
        // Current/pending events, transient condition flags, promises and damage windows have no replay contract.
        if (state.isEmpty()) return null;
        wire.put("State", state);
        return wire;
    }

    public static CompoundTag importData(CompoundTag wire, long now, long day) {
        if (!wire.contains("SchemaVersion", Tag.TAG_INT) || wire.getInt("SchemaVersion") != 1
                || !wire.contains("State", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("NPC migration schema/State is missing or unsupported");
        }
        CompoundTag state = wire.getCompound("State");
        CompoundTag restored = new CompoundTag();
        for (String key : state.getAllKeys()) {
            switch (key) {
                case "WorkTicks" -> {
                    require(state, key, Tag.TAG_LONG);
                    if (state.getLong(key) < 0) throw new IllegalArgumentException("NPC " + key);
                    restored.put(key, state.get(key).copy());
                }
                case "MealCount" -> {
                    require(state, key, Tag.TAG_INT);
                    if (state.getInt(key) < 0) throw new IllegalArgumentException("NPC " + key);
                    restored.put(key, state.get(key).copy());
                }
                case "FoodTypes" -> {
                    require(state, key, Tag.TAG_LIST);
                    var list = state.getList(key, Tag.TAG_STRING);
                    if (list.size() != ((net.minecraft.nbt.ListTag) state.get(key)).size())
                        throw new IllegalArgumentException("NPC FoodTypes entries");
                    restored.put(key, list.copy());
                }
                case "CooldownRemaining" -> {
                    require(state, key, Tag.TAG_COMPOUND);
                    CompoundTag cooldowns = new CompoundTag();
                    var remaining = state.getCompound(key);
                    for (String id : remaining.getAllKeys()) {
                        require(remaining, id, Tag.TAG_LONG);
                        long ticks = remaining.getLong(id);
                        if (ticks < 0) throw new IllegalArgumentException("NPC CooldownRemaining/" + id);
                        cooldowns.putLong(id, Math.addExact(now, ticks));
                    }
                    restored.put("Cooldowns", cooldowns);
                }
                default -> {
                    boolean known = false;
                    for (String name : ELAPSED) if (key.equals(name + "Elapsed")) {
                        require(state, key, Tag.TAG_LONG);
                        if (state.getLong(key) < 0) throw new IllegalArgumentException("NPC " + key);
                        restored.putLong(name, Math.subtractExact(now, state.getLong(key)));
                        known = true;
                    }
                    for (String name : DAYS) if (key.equals(name + "Offset")) {
                        require(state, key, Tag.TAG_LONG);
                        restored.putLong(name, Math.addExact(day, state.getLong(key)));
                        known = true;
                    }
                    if (!known) throw new IllegalArgumentException("Unknown NPC migration segment " + key);
                }
            }
        }
        return restored;
    }

    private static void require(CompoundTag data, String key, int type) {
        if (!data.contains(key, type)) throw new IllegalArgumentException("Malformed NPC segment " + key);
    }
}
