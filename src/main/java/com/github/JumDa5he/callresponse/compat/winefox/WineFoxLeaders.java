package com.github.JumDa5he.callresponse.compat.winefox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

/** Global death receipts, not a loaded-entity directory. Absence never means death. */
public final class WineFoxLeaders extends SavedData {
    private final Set<UUID> deadLeaders = new HashSet<>();
    private final Set<UUID> contributedMaids = new HashSet<>();
    // Keep receipts even after delivery. The entity saves its applied totals, so an ordinary
    // reload never heals twice and a temporarily unloaded leader never loses a contribution.
    private final Map<UUID, Contribution> contributions = new LinkedHashMap<>();
    private final Map<UUID, Totals> totals = new HashMap<>();

    public record Totals(double health, double attack) {
        public static final Totals ZERO = new Totals(0, 0);
    }
    private record Contribution(UUID leader, double health, double attack) {}

    public Totals totals(UUID leader) { return totals.getOrDefault(leader, Totals.ZERO); }

    public boolean contribute(UUID leader, UUID maid, double health, double attack) {
        if (isDead(leader) || !contributedMaids.add(maid)) return false;
        double hp = WineFoxGrowth.nonnegative(health), damage = WineFoxGrowth.nonnegative(attack);
        contributions.put(maid, new Contribution(leader, hp, damage));
        Totals old = totals(leader);
        totals.put(leader, new Totals(WineFoxGrowth.add(old.health, hp), WineFoxGrowth.add(old.attack, damage)));
        setDirty();
        return true;
    }

    public static WineFoxLeaders get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                WineFoxLeaders::load, WineFoxLeaders::new, "callresponse_wine_fox_leaders");
    }

    public boolean isDead(UUID leader) { return deadLeaders.contains(leader); }

    public void recordDeath(UUID leader) {
        if (deadLeaders.add(leader)) {
            totals.remove(leader);
            contributions.values().removeIf(c -> c.leader.equals(leader));
            setDirty();
        }
    }

    public static WineFoxLeaders load(CompoundTag tag) {
        WineFoxLeaders data = new WineFoxLeaders();
        ListTag list = tag.getList("DeadLeaders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("UUID")) data.deadLeaders.add(entry.getUUID("UUID"));
        }
        ListTag receipts = tag.getList("Contributions", Tag.TAG_COMPOUND);
        for (int i = 0; i < receipts.size(); i++) {
            CompoundTag entry = receipts.getCompound(i);
            if (entry.hasUUID("Leader") && entry.hasUUID("Maid"))
                data.contribute(entry.getUUID("Leader"), entry.getUUID("Maid"),
                        entry.getDouble("Health"), entry.getDouble("Attack"));
        }
        ListTag processed = tag.getList("ContributedMaids", Tag.TAG_COMPOUND);
        for (int i = 0; i < processed.size(); i++) {
            CompoundTag entry = processed.getCompound(i);
            if (entry.hasUUID("UUID")) data.contributedMaids.add(entry.getUUID("UUID"));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : deadLeaders) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", id);
            list.add(entry);
        }
        tag.put("DeadLeaders", list);
        ListTag receipts = new ListTag();
        contributions.forEach((maid, contribution) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Maid", maid);
            entry.putUUID("Leader", contribution.leader);
            entry.putDouble("Health", contribution.health);
            entry.putDouble("Attack", contribution.attack);
            receipts.add(entry);
        });
        tag.put("Contributions", receipts);
        ListTag processed = new ListTag();
        for (UUID maid : contributedMaids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("UUID", maid);
            processed.add(entry);
        }
        tag.put("ContributedMaids", processed);
        return tag;
    }
}
