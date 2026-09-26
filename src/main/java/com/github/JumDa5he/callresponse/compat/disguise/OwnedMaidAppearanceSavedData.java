package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persistent owner/model snapshots, so an unloaded maid need not be summoned for a disguise. */
public final class OwnedMaidAppearanceSavedData extends SavedData {
    private static final String NAME = "callresponse_owned_maid_appearances";
    private final Map<UUID, Map<UUID, DisguiseAppearance>> byOwner = new HashMap<>();
    private final Map<UUID, UUID> ownerByMaid = new HashMap<>();

    public static OwnedMaidAppearanceSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(OwnedMaidAppearanceSavedData::new,
                        OwnedMaidAppearanceSavedData::load, null), NAME);
    }

    private static OwnedMaidAppearanceSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        OwnedMaidAppearanceSavedData result = new OwnedMaidAppearanceSavedData();
        ListTag entries = root.getList("Maids", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Owner") || !entry.hasUUID("Maid")
                    || !entry.contains("Appearance", Tag.TAG_COMPOUND)) continue;
            UUID owner = entry.getUUID("Owner");
            UUID maid = entry.getUUID("Maid");
            DisguiseAppearance appearance = DisguiseAppearance.load(entry.getCompound("Appearance"));
            if (!appearance.usable()) continue;
            result.byOwner.computeIfAbsent(owner, ignored -> new HashMap<>()).put(maid, appearance);
            result.ownerByMaid.put(maid, owner);
        }
        return result;
    }

    public void observe(EntityMaid maid) {
        UUID maidId = maid.getUUID();
        UUID owner = maid.isAlive() && maid.isTame() ? maid.getOwnerUUID() : null;
        DisguiseAppearance appearance = owner == null ? null : DisguiseAppearance.capture(maid);
        UUID previousOwner = ownerByMaid.get(maidId);
        if (previousOwner != null && (!previousOwner.equals(owner) || appearance == null || !appearance.usable())) {
            remove(maidId);
        }
        if (owner == null || appearance == null || !appearance.usable()) return;
        DisguiseAppearance previous = byOwner.computeIfAbsent(owner, ignored -> new HashMap<>())
                .put(maidId, appearance);
        ownerByMaid.put(maidId, owner);
        if (!Objects.equals(previous, appearance)) setDirty();
    }

    public void remove(UUID maidId) {
        UUID owner = ownerByMaid.remove(maidId);
        if (owner == null) return;
        Map<UUID, DisguiseAppearance> appearances = byOwner.get(owner);
        if (appearances != null) {
            appearances.remove(maidId);
            if (appearances.isEmpty()) byOwner.remove(owner);
        }
        setDirty();
    }

    public Map<UUID, DisguiseAppearance> choices(UUID owner) {
        return Map.copyOf(byOwner.getOrDefault(owner, Map.of()));
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        byOwner.forEach((owner, maids) -> maids.forEach((maid, appearance) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putUUID("Maid", maid);
            entry.put("Appearance", appearance.save());
            entries.add(entry);
        }));
        root.put("Maids", entries);
        return root;
    }
}
