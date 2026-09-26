package com.github.JumDa5he.callresponse.compat.outpost;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
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

/** 世界级持久化记录，确保同一据点在重载与重启后都不会重复初始化。 */
public final class BetrayalOutpostSavedData extends SavedData {
    private static final String DATA_NAME = "callresponse_betrayal_outposts";
    private final Set<String> initialized = new HashSet<>();
    private final Map<String, Outpost> structures = new HashMap<>();
    private final Map<String, Map<Long, List<Outpost>>> nearbyChunks = new HashMap<>();
    private final Map<String, LootChest> lootChests = new HashMap<>();
    private final Set<String> settledChests = new HashSet<>();
    private final Set<String> removedChests = new HashSet<>();

    public record LootChest(String campKey, String dimension, BlockPos pos,
                            ResourceLocation lootTable, long lootSeed) {}

    public record Outpost(String key, String dimension, BoundingBox box, BlockPos center) {
        private boolean contains(BlockPos pos) {
            return pos.getX() >= box.minX() - 8 && pos.getX() <= box.maxX() + 8
                    && pos.getZ() >= box.minZ() - 8 && pos.getZ() <= box.maxZ() + 8
                    && pos.getY() >= box.minY() - 8 && pos.getY() <= box.maxY() + 8;
        }
    }

    public static BetrayalOutpostSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BetrayalOutpostSavedData::new,
                        BetrayalOutpostSavedData::load, null), DATA_NAME);
    }

    private static BetrayalOutpostSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        BetrayalOutpostSavedData data = new BetrayalOutpostSavedData();
        ListTag list = tag.getList("Initialized", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) data.initialized.add(list.getString(i));
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
                    BlockPos.of(entry.getLong("Pos")), table, entry.getLong("Seed"));
            String chestKey = chestKey(chest.dimension(), chest.pos());
            data.lootChests.put(chestKey, chest);
            if (entry.getBoolean("Settled")) data.settledChests.add(chestKey);
            if (entry.getBoolean("Removed")) data.removedChests.add(chestKey);
        }
        return data;
    }

    public boolean contains(String key) {
        return initialized.contains(key);
    }

    public void markInitialized(String key) {
        if (initialized.add(key)) setDirty();
    }

    /** 只登记世界生成系统给出的真实 StructureStart，不依靠方块形状判断。 */
    public void registerStructure(String key, ServerLevel level, BoundingBox box, int centerY) {
        if (structures.containsKey(key)) return;
        addStructure(new Outpost(key, level.dimension().location().toString(), box,
                new BlockPos((box.minX() + box.maxX()) / 2, centerY, (box.minZ() + box.maxZ()) / 2)));
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

    public Outpost findByKey(ServerLevel level, String key) {
        return initialized.contains(key) ? findLootCampByKey(level, key) : null;
    }

    /** The generated camp and its native loot exist even before the maid group is initialized. */
    public Outpost findLootCampByKey(ServerLevel level, String key) {
        Outpost outpost = structures.get(key);
        return outpost != null && outpost.dimension().equals(level.dimension().location().toString()) ? outpost : null;
    }

    private static String chestKey(String dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }

    public void registerLootChest(ServerLevel level, String campKey, BlockPos pos,
                                  ResourceLocation table, long seed) {
        if (findLootCampByKey(level, campKey) == null) return;
        String dimension = level.dimension().location().toString();
        String key = chestKey(dimension, pos);
        if (lootChests.putIfAbsent(key, new LootChest(campKey, dimension, pos.immutable(), table, seed)) == null) {
            setDirty();
        }
    }

    /** Exact positions captured during structure initialization, never inferred from player-placed chests. */
    public LootChest lootChest(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        String key = chestKey(dimension, pos);
        LootChest chest = lootChests.get(key);
        if (chest == null) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(state));
                BlockState partnerState = level.getBlockState(partner);
                if (partnerState.is(state.getBlock())
                        && partnerState.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)
                        && partnerState.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE).getOpposite()) {
                    chest = lootChests.get(chestKey(dimension, partner));
                    if (chest != null) key = chestKey(dimension, chest.pos());
                }
            }
        }
        return chest != null && !removedChests.contains(key)
                && findLootCampByKey(level, chest.campKey()) != null ? chest : null;
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
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        initialized.forEach(value -> list.add(StringTag.valueOf(value)));
        tag.put("Initialized", list);
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
            entry.putString("Table", chest.lootTable().toString());
            entry.putLong("Seed", chest.lootSeed());
            entry.putBoolean("Settled", settledChests.contains(indexed.getKey()));
            entry.putBoolean("Removed", removedChests.contains(indexed.getKey()));
            chests.add(entry);
        }
        tag.put("LootChests", chests);
        return tag;
    }
}
