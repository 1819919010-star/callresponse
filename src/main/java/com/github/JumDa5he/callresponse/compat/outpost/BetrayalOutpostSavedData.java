package com.github.JumDa5he.callresponse.compat.outpost;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 世界级持久化记录，确保同一据点在重载与重启后都不会重复初始化。 */
public final class BetrayalOutpostSavedData extends SavedData {
    private static final String DATA_NAME = "callresponse_betrayal_outposts";
    private final Set<String> initialized = new HashSet<>();
    private final Set<String> skippedLegacy = new HashSet<>();
    private final Map<String, Outpost> structures = new HashMap<>();
    private final Map<String, Map<Long, List<Outpost>>> nearbyChunks = new HashMap<>();
    private final Map<String, LootChest> lootChests = new HashMap<>();
    private final Map<String, String> chestAliases = new HashMap<>();
    private final Set<String> settledChests = new HashSet<>();
    private final Set<String> removedChests = new HashSet<>();
    private final Map<String, CampPlan> campPlans = new HashMap<>();
    private final Map<UUID, Set<UUID>> pendingClearRewards = new HashMap<>();

    public record LootChest(String campKey, String dimension, BlockPos pos,
                            BlockPos otherHalf, ResourceLocation lootTable, long lootSeed) {}

    public static final class CampPlan {
        private final List<SpawnSlot> slots;
        private UUID referencePlayer;
        private CompoundTag markerLayout = new CompoundTag();
        private UUID instanceId;
        private boolean sourceKnown;
        private boolean natural;
        private boolean cleared;
        private boolean rewardSettled;
        private BlockPos storyChest;
        private boolean storyChoiceKnown;
        private boolean recruitedStory;
        private boolean normalStoryIssued;
        private boolean glyStoryEligible;
        private boolean glyStoryIssued;
        private final Set<UUID> deadGuards = new HashSet<>();
        private final Set<UUID> participants = new HashSet<>();

        private CampPlan(List<SpawnSlot> slots, UUID referencePlayer, UUID instanceId) {
            this.slots = slots;
            this.referencePlayer = referencePlayer;
            this.instanceId = instanceId;
        }

        public List<SpawnSlot> slots() { return List.copyOf(slots); }
        public UUID referencePlayer() { return referencePlayer; }
        public CompoundTag markerLayout() { return markerLayout.copy(); }
        public UUID instanceId() { return instanceId; }
        public boolean natural() { return natural; }
        public boolean cleared() { return cleared; }
        public boolean rewardSettled() { return rewardSettled; }
        public Set<UUID> participants() { return Set.copyOf(participants); }
        public Set<UUID> deadGuards() { return Set.copyOf(deadGuards); }
        public boolean isGuard(UUID entity) {
            return slots.stream().anyMatch(slot -> slot.entityId().equals(entity) && isCombatRole(slot.role()));
        }
        public boolean completeGuardRoster() {
            return slots.size() == 5 && slots.stream().map(SpawnSlot::entityId).distinct().count() == 5
                    && slots.stream().allMatch(slot -> "GLY".equals(slot.role()) || isCombatRole(slot.role()) && slot.spawned())
                    && slots.stream().anyMatch(slot -> isCombatRole(slot.role()));
        }
        public boolean allGuardsConfirmedDead() {
            return completeGuardRoster() && slots.stream().filter(slot -> isCombatRole(slot.role()))
                    .allMatch(slot -> deadGuards.contains(slot.entityId()));
        }
        private static boolean isCombatRole(String role) {
            return "HEAVY".equals(role) || "SWORDSMAN".equals(role) || "FARMER".equals(role) || "FEEDER".equals(role);
        }
        public boolean needsSpawnWork() {
            return slots.stream().anyMatch(slot -> !slot.spawned() && slot.attempts() < 3);
        }
        public BlockPos storyChest() { return storyChest; }
        public boolean recruitedStory() { return recruitedStory; }
        public boolean normalStoryIssued() { return normalStoryIssued; }
        public boolean hasGlyStory() {
            // Older Lootr conversions could retire the shared chest before GLY joined.
            return glyStoryEligible || slots.stream().anyMatch(slot -> "GLY".equals(slot.role()) && slot.spawned());
        }
        public boolean awaitsGlyStory() {
            return !hasGlyStory() && slots.stream().anyMatch(slot ->
                    "GLY".equals(slot.role()) && !slot.spawned() && slot.attempts() < 3);
        }
        public boolean needsStoryWork() {
            return storyChest != null && (!normalStoryIssued || glyStoryEligible && !glyStoryIssued);
        }
        public boolean needsInitialization() { return needsSpawnWork() || needsStoryWork(); }
        public boolean needsPersonalization() {
            return slots.stream().anyMatch(slot -> slot.spawned() && !slot.personalized());
        }
    }

    public record SpawnSlot(int candidate, String role, UUID entityId, int attempts,
                            boolean spawned, boolean personalized) {}

    public record Outpost(String key, String dimension, BoundingBox box, BlockPos center) {
        private boolean contains(BlockPos pos) {
            return pos.getX() >= box.minX() - 8 && pos.getX() <= box.maxX() + 8
                    && pos.getZ() >= box.minZ() - 8 && pos.getZ() <= box.maxZ() + 8
                    && pos.getY() >= box.minY() - 8 && pos.getY() <= box.maxY() + 8;
        }
    }

    public static BetrayalOutpostSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                BetrayalOutpostSavedData::load, BetrayalOutpostSavedData::new, DATA_NAME);
    }

    private static BetrayalOutpostSavedData load(CompoundTag tag) {
        BetrayalOutpostSavedData data = new BetrayalOutpostSavedData();
        ListTag list = tag.getList("Initialized", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) data.initialized.add(list.getString(i));
        ListTag skipped = tag.getList("SkippedLegacy", Tag.TAG_STRING);
        for (int i = 0; i < skipped.size(); i++) data.skippedLegacy.add(skipped.getString(i));
        ListTag structures = tag.getList("Structures", Tag.TAG_COMPOUND);
        for (int i = 0; i < structures.size(); i++) {
            CompoundTag entry = structures.getCompound(i);
            BoundingBox box = new BoundingBox(entry.getInt("MinX"), entry.getInt("MinY"), entry.getInt("MinZ"),
                    entry.getInt("MaxX"), entry.getInt("MaxY"), entry.getInt("MaxZ"));
            data.addStructure(new Outpost(entry.getString("Key"), entry.getString("Dimension"), box,
                    BlockPos.of(entry.getLong("Center"))));
        }
        ListTag chests = tag.getList("LootChests", Tag.TAG_COMPOUND);
        for (int i = 0; i < chests.size(); i++) {
            CompoundTag entry = chests.getCompound(i);
            ResourceLocation table = ResourceLocation.tryParse(entry.getString("Table"));
            if (table == null) continue;
            LootChest chest = new LootChest(entry.getString("Camp"), entry.getString("Dimension"),
                    BlockPos.of(entry.getLong("Pos")), entry.contains("OtherHalf", Tag.TAG_LONG)
                    ? BlockPos.of(entry.getLong("OtherHalf")) : null, table, entry.getLong("Seed"));
            String chestKey = chestKey(chest.dimension(), chest.pos());
            data.lootChests.put(chestKey, chest);
            if (chest.otherHalf() != null) {
                data.chestAliases.put(chestKey(chest.dimension(), chest.otherHalf()), chestKey);
            }
            if (entry.getBoolean("Settled")) data.settledChests.add(chestKey);
            if (entry.getBoolean("Removed")) data.removedChests.add(chestKey);
        }
        ListTag plans = tag.getList("CampPlans", Tag.TAG_COMPOUND);
        for (int i = 0; i < plans.size(); i++) {
            CompoundTag entry = plans.getCompound(i);
            List<SpawnSlot> slots = new ArrayList<>();
            ListTag savedSlots = entry.getList("Slots", Tag.TAG_COMPOUND);
            for (int j = 0; j < savedSlots.size(); j++) {
                CompoundTag slot = savedSlots.getCompound(j);
                try {
                    slots.add(new SpawnSlot(slot.getInt("Candidate"), slot.getString("Role"),
                            slot.getUUID("Entity"), slot.getInt("Attempts"),
                            slot.getBoolean("Spawned"), slot.getBoolean("Personalized")));
                } catch (IllegalArgumentException ignored) {
                    // Broken callresponse-only progress must not make the world unloadable.
                }
            }
            // An empty roster is valid only for a natural structure explicitly saved as unspawnable.
            if (slots.size() != 5 && !(entry.getBoolean("UnspawnableStructure") && savedSlots.isEmpty()
                    && entry.contains("Slots", Tag.TAG_LIST) && entry.getBoolean("SourceKnown")
                    && entry.getBoolean("Natural"))) continue;
            UUID reference = entry.hasUUID("ReferencePlayer") ? entry.getUUID("ReferencePlayer") : null;
            UUID instance = entry.hasUUID("Instance") ? entry.getUUID("Instance")
                    : UUID.nameUUIDFromBytes(("callresponse:camp-instance|" + entry.getString("Key"))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            CampPlan plan = new CampPlan(slots, reference, instance);
            plan.markerLayout = entry.getCompound("MarkerLayout").copy();
            plan.sourceKnown = entry.getBoolean("SourceKnown");
            plan.natural = plan.sourceKnown && entry.getBoolean("Natural");
            plan.cleared = entry.getBoolean("Cleared");
            plan.rewardSettled = entry.getBoolean("RewardSettled");
            if (entry.contains("StoryChest", Tag.TAG_LONG)) plan.storyChest = BlockPos.of(entry.getLong("StoryChest"));
            plan.recruitedStory = entry.getBoolean("RecruitedStory");
            plan.normalStoryIssued = entry.getBoolean("NormalStoryIssued");
            plan.glyStoryEligible = entry.getBoolean("GlyStoryEligible");
            plan.glyStoryIssued = entry.getBoolean("GlyStoryIssued");
            plan.storyChoiceKnown = entry.getBoolean("StoryChoiceKnown") || plan.storyChest != null
                    || plan.recruitedStory || plan.normalStoryIssued || plan.glyStoryEligible || plan.glyStoryIssued;
            readIds(entry, "DeadGuards", plan.deadGuards);
            readIds(entry, "Participants", plan.participants);
            data.campPlans.put(entry.getString("Key"), plan);
        }
        ListTag pending = tag.getList("PendingClearRewards", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (!entry.hasUUID("Player")) continue;
            Set<UUID> camps = new HashSet<>();
            readIds(entry, "Camps", camps);
            if (!camps.isEmpty()) data.pendingClearRewards.put(entry.getUUID("Player"), camps);
        }
        return data;
    }

    public boolean contains(String key) {
        return initialized.contains(key);
    }

    public boolean isLegacySkipped(String key) { return skippedLegacy.contains(key); }

    public void skipLegacy(String key) {
        if (skippedLegacy.add(key)) setDirty();
    }

    public void markInitialized(String key) {
        if (initialized.add(key)) setDirty();
    }

    public CampPlan plan(String key) { return campPlans.get(key); }

    record StoryCamp(String key, CampPlan plan, boolean choiceKnown) {}

    StoryCamp storyCampAt(ServerLevel level, BlockPos pos, ResourceLocation table, boolean personalChest) {
        String dim = level.dimension().location().toString();
        String chestKey = chestKey(dim, pos);
        // Even after a prior Lootr conversion retired the shared-chest receipt, its captured
        // table binding and normal-story choice still describe this exact original position.
        LootChest bound = lootChests.get(chestKey);
        if (bound != null && table.equals(bound.lootTable()) && (personalChest || !removedChests.contains(chestKey))) {
            CampPlan plan = campPlans.get(bound.campKey());
            if (plan != null) return new StoryCamp(bound.campKey(), plan, plan.storyChoiceKnown);
        }
        if (!personalChest) return null;
        var index = nearbyChunks.get(dim);
        if (index == null) return null;
        for (Outpost camp : index.getOrDefault(ChunkPos.asLong(pos), List.of())) {
            if (!camp.box().isInside(pos)) continue;
            CampPlan plan = campPlans.get(camp.key());
            if (plan != null && (plan.storyChest == null || plan.storyChest.equals(pos)))
                return new StoryCamp(camp.key(), plan, plan.storyChoiceKnown);
        }
        return null;
    }

    public List<Outpost> pendingInChunk(ServerLevel level, long chunk) {
        var index = nearbyChunks.get(level.dimension().location().toString());
        if (index == null) return List.of();
        return index.getOrDefault(chunk, List.of()).stream().filter(outpost -> {
            CampPlan plan = campPlans.get(outpost.key());
            return !skippedLegacy.contains(outpost.key()) && plan != null
                    && !plan.markerLayout.isEmpty() && plan.needsInitialization();
        }).toList();
    }

    public void createPlan(String key, List<SpawnSlot> slots, UUID referencePlayer) {
        createPlan(key, slots, referencePlayer, false);
    }

    public void createPlan(String key, List<SpawnSlot> slots, UUID referencePlayer, boolean natural) {
        if (slots.size() != 5) throw new IllegalArgumentException("Camp requires five spawn slots");
        CampPlan plan = new CampPlan(new ArrayList<>(slots), referencePlayer, UUID.randomUUID());
        plan.sourceKnown = true;
        plan.natural = natural;
        if (campPlans.putIfAbsent(key, plan) == null) setDirty();
    }

    /** Keeps the structure searchable when a verified natural start could not finish initialization. */
    public void registerUnspawnableNaturalStructure(String key, ServerLevel level, BoundingBox box) {
        CampPlan plan = campPlans.get(key);
        if (plan == null) {
            plan = new CampPlan(List.of(), null, UUID.randomUUID());
            plan.sourceKnown = true;
            plan.natural = true;
            campPlans.put(key, plan);
            setDirty();
        } else if (!plan.natural()) {
            return;
        }
        markInitialized(key);
        registerStructure(key, level, box, (box.minY() + box.maxY()) / 2);
    }

    /** A genuine saved StructureStart may identify old plans; debug plans are explicitly non-natural. */
    public void confirmNaturalStructure(String key) {
        CampPlan plan = campPlans.get(key);
        if (plan != null && !plan.sourceKnown) {
            plan.sourceKnown = true;
            plan.natural = true;
            setDirty();
        }
    }

    public boolean recordParticipant(String key, UUID guard, UUID player) {
        CampPlan plan = campPlans.get(key);
        if (plan == null || !plan.natural || plan.rewardSettled || !plan.isGuard(guard)
                || player == null || player.equals(new UUID(0, 0))) return false;
        boolean added = plan.participants.add(player);
        if (added) setDirty();
        return added;
    }

    /** Only the existing confirmed native death hook supplies this receipt; absence never does. */
    public boolean confirmGuardDeath(String key, UUID guard) {
        CampPlan plan = campPlans.get(key);
        if (plan == null || !plan.natural || plan.rewardSettled || !plan.isGuard(guard)) return false;
        boolean added = plan.deadGuards.add(guard);
        if (added) setDirty();
        return added;
    }

    /** Completion and reward suppression are final, even if the protection setting changes later. */
    public boolean settleClearedCamp(String key, boolean rewardsAllowed) {
        return settleClearedCamp(key, rewardsAllowed, false, Set.of());
    }

    /** The server verifies the entity-loaded structure before supplying areaCleared. */
    public boolean settleClearedCamp(String key, boolean rewardsAllowed, boolean areaCleared, Set<UUID> playersInside) {
        CampPlan plan = campPlans.get(key);
        if (plan == null || !plan.natural || plan.rewardSettled || !(areaCleared || plan.allGuardsConfirmedDead())) return false;
        plan.cleared = true;
        plan.rewardSettled = true;
        if (rewardsAllowed) {
            Set<UUID> recipients = new HashSet<>(plan.participants);
            recipients.addAll(playersInside);
            for (UUID player : recipients) {
                pendingClearRewards.computeIfAbsent(player, ignored -> new HashSet<>()).add(plan.instanceId);
            }
        }
        setDirty();
        return true;
    }

    public Set<UUID> pendingClearRewards(UUID player) {
        return Set.copyOf(pendingClearRewards.getOrDefault(player, Set.of()));
    }

    public void consumeClearReward(UUID player, UUID campInstance) {
        Set<UUID> pending = pendingClearRewards.get(player);
        if (pending != null && pending.remove(campInstance)) {
            if (pending.isEmpty()) pendingClearRewards.remove(player);
            setDirty();
        }
    }

    private static void readIds(CompoundTag parent, String key, Set<UUID> target) {
        ListTag entries = parent.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Id")) target.add(entry.getUUID("Id"));
        }
    }

    private static ListTag writeIds(Set<UUID> ids) {
        ListTag entries = new ListTag();
        for (UUID id : ids) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entries.add(entry);
        }
        return entries;
    }

    public void setReferencePlayer(String key, UUID player) {
        CampPlan plan = campPlans.get(key);
        if (plan != null && plan.referencePlayer == null) {
            plan.referencePlayer = player;
            setDirty();
        }
    }

    public void setMarkerLayout(String key, CompoundTag layout) {
        CampPlan plan = campPlans.get(key);
        if (plan != null && plan.markerLayout.isEmpty()) {
            plan.markerLayout = layout.copy();
            setDirty();
        }
    }

    public void setStoryChest(CampPlan plan, BlockPos pos, boolean recruited) {
        if (plan.storyChest != null) return;
        plan.storyChest = pos.immutable();
        plan.storyChoiceKnown = true;
        plan.recruitedStory = recruited;
        setDirty();
    }

    public void confirmStoryGly(CampPlan plan) {
        if (plan.storyChest != null && !plan.glyStoryEligible) {
            plan.glyStoryEligible = true;
            setDirty();
        }
    }

    public void retireStoryChest(CampPlan plan) {
        if (plan.storyChest != null) {
            plan.storyChest = null;
            setDirty();
        }
    }

    public void markStoryIssued(CampPlan plan, boolean gly) {
        if (gly) plan.glyStoryIssued = true;
        else plan.normalStoryIssued = true;
        setDirty();
    }

    public void updateSlot(String key, int index, SpawnSlot slot) {
        CampPlan plan = campPlans.get(key);
        if (plan != null && index >= 0 && index < plan.slots.size()) {
            plan.slots.set(index, slot);
            setDirty();
        }
    }

    /** 只登记世界生成系统给出的真实 StructureStart，不依靠方块形状判断。 */
    public void registerStructure(String key, ServerLevel level, BoundingBox box, int centerY) {
        registerStructure(key, level, box, new BlockPos((box.minX() + box.maxX()) / 2,
                centerY, (box.minZ() + box.maxZ()) / 2));
    }

    public void registerStructure(String key, ServerLevel level, BoundingBox box, BlockPos center) {
        if (!initialized.contains(key) || structures.containsKey(key)) return;
        addStructure(new Outpost(key, level.dimension().location().toString(), box, center.immutable()));
        setDirty();
    }

    public Outpost findAt(ServerLevel level, BlockPos pos) {
        Map<Long, List<Outpost>> index = nearbyChunks.get(level.dimension().location().toString());
        if (index == null) return null;
        for (Outpost outpost : index.getOrDefault(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), List.of())) {
            if (initialized.contains(outpost.key()) && outpost.contains(pos)) return outpost;
        }
        return null;
    }

    public List<Outpost> unsettledNaturalOutpostsAt(ServerLevel level, BlockPos pos) {
        var index = nearbyChunks.get(level.dimension().location().toString());
        if (index == null) return List.of();
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        return index.getOrDefault(chunk, List.of()).stream().filter(outpost -> {
            CampPlan plan = campPlans.get(outpost.key());
            return plan != null && plan.natural && !plan.rewardSettled && outpost.box().isInside(pos);
        }).toList();
    }

    public Outpost findByKey(ServerLevel level, String key) {
        Outpost outpost = structures.get(key);
        return outpost != null && initialized.contains(key)
                && outpost.dimension().equals(level.dimension().location().toString()) ? outpost : null;
    }

    private static String chestKey(String dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }

    public void registerLootChest(ServerLevel level, String campKey, BlockPos pos,
                                  ResourceLocation table, long seed) {
        registerLootChest(level, campKey, pos, null, table, seed);
    }

    public void registerLootChest(ServerLevel level, String campKey, BlockPos pos, BlockPos otherHalf,
                                  ResourceLocation table, long seed) {
        if (!initialized.contains(campKey)) return;
        String dimension = level.dimension().location().toString();
        String key = chestKey(dimension, pos);
        if (lootChests.putIfAbsent(key, new LootChest(campKey, dimension, pos.immutable(),
                otherHalf == null ? null : otherHalf.immutable(), table, seed)) == null) {
            if (otherHalf != null) chestAliases.put(chestKey(dimension, otherHalf), key);
            setDirty();
        }
    }

    /** Exact positions captured during structure initialization, never inferred from player-placed chests. */
    public LootChest lootChest(ServerLevel level, BlockPos pos) {
        String key = chestKey(level.dimension().location().toString(), pos);
        key = chestAliases.getOrDefault(key, key);
        LootChest chest = lootChests.get(key);
        return chest != null && !removedChests.contains(key)
                && findByKey(level, chest.campKey()) != null ? chest : null;
    }

    public boolean isChestSettled(LootChest chest) {
        return settledChests.contains(chestKey(chest.dimension(), chest.pos()));
    }

    public void settleChest(LootChest chest) {
        if (settledChests.add(chestKey(chest.dimension(), chest.pos()))) setDirty();
    }

    public void removeChest(LootChest chest) {
        String key = chestKey(chest.dimension(), chest.pos());
        boolean newlySettled = settledChests.add(key);
        boolean newlyRemoved = removedChests.add(key);
        var plan = campPlans.get(chest.campKey());
        if (plan != null && chest.pos().equals(plan.storyChest)) retireStoryChest(plan);
        if (newlySettled || newlyRemoved) setDirty();
    }

    private void addStructure(Outpost outpost) {
        if (structures.putIfAbsent(outpost.key(), outpost) != null) return;
        Map<Long, List<Outpost>> index = nearbyChunks.computeIfAbsent(outpost.dimension(), ignored -> new HashMap<>());
        BoundingBox box = outpost.box();
        for (int x = (box.minX() - 8) >> 4; x <= (box.maxX() + 8) >> 4; x++) {
            for (int z = (box.minZ() - 8) >> 4; z <= (box.maxZ() + 8) >> 4; z++) {
                index.computeIfAbsent(ChunkPos.asLong(x, z), ignored -> new ArrayList<>()).add(outpost);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        initialized.forEach(value -> list.add(StringTag.valueOf(value)));
        tag.put("Initialized", list);
        ListTag skipped = new ListTag();
        skippedLegacy.forEach(value -> skipped.add(StringTag.valueOf(value)));
        tag.put("SkippedLegacy", skipped);
        ListTag structureList = new ListTag();
        for (Outpost outpost : structures.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", outpost.key());
            entry.putString("Dimension", outpost.dimension());
            entry.putInt("MinX", outpost.box().minX());
            entry.putInt("MinY", outpost.box().minY());
            entry.putInt("MinZ", outpost.box().minZ());
            entry.putInt("MaxX", outpost.box().maxX());
            entry.putInt("MaxY", outpost.box().maxY());
            entry.putInt("MaxZ", outpost.box().maxZ());
            entry.putLong("Center", outpost.center().asLong());
            structureList.add(entry);
        }
        tag.put("Structures", structureList);
        ListTag chests = new ListTag();
        for (Map.Entry<String, LootChest> indexed : lootChests.entrySet()) {
            LootChest chest = indexed.getValue();
            CompoundTag entry = new CompoundTag();
            entry.putString("Camp", chest.campKey());
            entry.putString("Dimension", chest.dimension());
            entry.putLong("Pos", chest.pos().asLong());
            if (chest.otherHalf() != null) entry.putLong("OtherHalf", chest.otherHalf().asLong());
            entry.putString("Table", chest.lootTable().toString());
            entry.putLong("Seed", chest.lootSeed());
            entry.putBoolean("Settled", settledChests.contains(indexed.getKey()));
            entry.putBoolean("Removed", removedChests.contains(indexed.getKey()));
            chests.add(entry);
        }
        tag.put("LootChests", chests);
        ListTag plans = new ListTag();
        for (Map.Entry<String, CampPlan> indexed : campPlans.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", indexed.getKey());
            CampPlan plan = indexed.getValue();
            entry.putUUID("Instance", plan.instanceId);
            entry.putBoolean("SourceKnown", plan.sourceKnown);
            entry.putBoolean("Natural", plan.natural);
            if (plan.slots.isEmpty()) entry.putBoolean("UnspawnableStructure", true);
            entry.putBoolean("Cleared", plan.cleared);
            entry.putBoolean("RewardSettled", plan.rewardSettled);
            if (plan.storyChest != null) entry.putLong("StoryChest", plan.storyChest.asLong());
            if (plan.storyChoiceKnown) entry.putBoolean("StoryChoiceKnown", true);
            entry.putBoolean("RecruitedStory", plan.recruitedStory);
            entry.putBoolean("NormalStoryIssued", plan.normalStoryIssued);
            entry.putBoolean("GlyStoryEligible", plan.glyStoryEligible);
            entry.putBoolean("GlyStoryIssued", plan.glyStoryIssued);
            entry.put("DeadGuards", writeIds(plan.deadGuards));
            entry.put("Participants", writeIds(plan.participants));
            if (!indexed.getValue().markerLayout.isEmpty()) {
                entry.put("MarkerLayout", indexed.getValue().markerLayout.copy());
            }
            if (indexed.getValue().referencePlayer != null) {
                entry.putUUID("ReferencePlayer", indexed.getValue().referencePlayer);
            }
            ListTag slots = new ListTag();
            for (SpawnSlot slot : indexed.getValue().slots) {
                CompoundTag saved = new CompoundTag();
                saved.putInt("Candidate", slot.candidate());
                saved.putString("Role", slot.role());
                saved.putUUID("Entity", slot.entityId());
                saved.putInt("Attempts", slot.attempts());
                saved.putBoolean("Spawned", slot.spawned());
                saved.putBoolean("Personalized", slot.personalized());
                slots.add(saved);
            }
            entry.put("Slots", slots);
            plans.add(entry);
        }
        tag.put("CampPlans", plans);
        ListTag pending = new ListTag();
        pendingClearRewards.forEach((player, camps) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.put("Camps", writeIds(camps));
            pending.add(entry);
        });
        tag.put("PendingClearRewards", pending);
        return tag;
    }
}
