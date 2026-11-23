package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Independent camp ledger, including spent reward rights after adoption/death/revival. */
public final class WanderingCampSavedData extends SavedData {
    private CompoundTag camps = new CompoundTag();
    private final Map<String, ArrayDeque<Long>> pendingBooks = new HashMap<>();
    public static WanderingCampSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(tag -> {
            var data = new WanderingCampSavedData();
            data.camps = tag.getCompound("Camps");
            CompoundTag pending = tag.getCompound("HeldDiaryChests");
            for (String dim : pending.getAllKeys()) {
                var queue = new ArrayDeque<Long>();
                for (long pos : pending.getLongArray(dim)) queue.addLast(pos);
                if (!queue.isEmpty()) data.pendingBooks.put(dim, queue);
            }
            return data;
        }, WanderingCampSavedData::new, "callresponse_wandering_camps");
    }
    public CompoundTag entry(String id) {
        if (!camps.contains(id)) { camps.put(id, new CompoundTag()); setDirty(); }
        return camps.getCompound(id);
    }
    public boolean claim(String id) {
        if (id.isEmpty()) return false;
        CompoundTag c = entry(id);
        if (c.getBoolean("RewardSpent")) return false;
        c.putBoolean("RewardSpent", true);
        setDirty();
        return true;
    }

    CompoundTag findEntry(String id) {
        return camps.contains(id, Tag.TAG_COMPOUND) ? camps.getCompound(id) : null;
    }

    void deferStoryBook(ServerLevel level, BlockPos pos) {
        // Called only for the first native roll (the chest carries the receipt for later fills).
        pendingBooks.computeIfAbsent(level.dimension().location().toString(), ignored -> new ArrayDeque<>())
                .addLast(pos.asLong());
        setDirty();
    }

    void retryStoryBooks(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        ArrayDeque<Long> queue = pendingBooks.get(dimension);
        if (queue == null) return;
        int budget = Math.min(4, queue.size());
        for (int i = 0; i < budget; i++) {
            long pos = queue.removeFirst();
            if (WanderingCampStory.deliverIfHeld(level, BlockPos.of(pos))) setDirty();
            else queue.addLast(pos);
        }
        if (queue.isEmpty()) pendingBooks.remove(dimension);
    }

    @Override public CompoundTag save(CompoundTag tag) {
        tag.put("Camps", camps);
        CompoundTag pending = new CompoundTag();
        pendingBooks.forEach((dim, queue) -> pending.putLongArray(dim, queue.stream().mapToLong(Long::longValue).toArray()));
        tag.put("HeldDiaryChests", pending);
        return tag;
    }
}
