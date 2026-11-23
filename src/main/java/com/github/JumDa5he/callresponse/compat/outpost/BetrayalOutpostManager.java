package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.story.CampStoryBooks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.IOException;
import java.util.UUID;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.sign.MaidSignManager;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.item.DyeColor;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** 登记结构自带的战利品容器，并初始化五名复仇女仆。 */
public final class BetrayalOutpostManager {
    public static final ResourceLocation STRUCTURE_ID = ResourceLocation.fromNamespaceAndPath("callresponse", "betrayal_maid_outpost");
    private static final ResourceKey<LootTable> KITCHEN_LOOT = lootKey("betrayal_outpost_kitchen");
    private static final ResourceKey<LootTable> STORAGE_LOOT = lootKey("betrayal_outpost_storage");
    private static final ResourceKey<LootTable> ENCHANTED_LOOT = lootKey("betrayal_outpost_enchanted");
    private static final ResourceKey<LootTable> VALUABLE_LOOT = lootKey("betrayal_outpost_valuables");
    private static final ResourceKey<LootTable> STORY_LOOT = lootKey("betrayal_outpost_story");

    private static ResourceKey<LootTable> lootKey(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath("callresponse", "chests/" + path));
    }

    private static final Set<ResourceKey<LootTable>> OUTPOST_LOOT = Set.of(
            KITCHEN_LOOT, STORAGE_LOOT, ENCHANTED_LOOT, VALUABLE_LOOT, STORY_LOOT);
    private static final int MAID_COUNT = 5;
    private static BetrayalOutpostManager instance;
    private static final ResourceLocation LAYOUT_ID = ResourceLocation.fromNamespaceAndPath("callresponse", "outpost/betrayal_maid_outpost_layout.json");
    private static final ResourceLocation STORY_TABLE = ResourceLocation.fromNamespaceAndPath("callresponse", "chests/betrayal_outpost_story");
    private static final String GLY_BOOK_RECEIPT = "callresponse:outpost_gly_book_issued";
    private static final int MAX_SPAWN_ATTEMPTS = 3;
    private static final long RETRY_TICKS = 20L;
    public static final int NATURAL_CLEAR_REWARD_TICKS = 72000;
    private static final Set<UUID> DELIVERING_CLEAR_REWARDS = new java.util.HashSet<>();

    private final Map<ResourceKey<Level>, Queue<Long>> pendingChunks = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, Set<Long>> queuedChunks = new ConcurrentHashMap<>();

    public BetrayalOutpostManager() {
        instance = this;
        // 世界读取实体 NBT 之前先准备好允许上限，避免真实大生命在加载时被原版 1024 裁剪。
        BetrayalOutpostMaxHealth.ensureAndGetLimit();
    }
    private final Map<ResourceKey<Level>, Map<String, PendingOutpost>> pendingOutposts = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onOutpostMaidJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel
                && event.getEntity() instanceof EntityMaid maid
                && BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            ((OutpostMaidMarker) maid).callresponse$setOutpostMaid(true);
            BetrayalOutpostMaidData.ensureUntamed(maid);
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        ChunkPos pos = chunk.getPos();
        // Queue actual starts from this already loaded chunk, never a hard-coded spacing/salt candidate.
        // Resolve the camp identity on the existing server-tick path; do not load other chunks here.
        long packed = pos.toLong();
        Set<Long> queued = queuedChunks.computeIfAbsent(level.dimension(), ignored -> ConcurrentHashMap.newKeySet());
        if (queued.add(packed)) {
            pendingChunks.computeIfAbsent(level.dimension(), ignored -> new ConcurrentLinkedQueue<>()).offer(packed);
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        for (int i = 0; i < 8; i++) inspectOneLoadedChunk(level);
        initializeOneReadyOutpost(level);
        if (level.getGameTime() % 20 == 0) checkNaturalCampArea(level);
    }

    private void inspectOneLoadedChunk(ServerLevel level) {
        Queue<Long> queue = pendingChunks.get(level.dimension());
        if (queue == null) return;
        Long packed = queue.poll();
        if (packed == null) return;
        Set<Long> queued = queuedChunks.get(level.dimension());
        if (queued != null) queued.remove(packed);
        LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
        if (chunk == null) return;

        // Command-placed camps have no vanilla StructureStart. Resume their persisted slots too.
        for (var saved : BetrayalOutpostSavedData.get(level).pendingInChunk(level, packed)) {
            pendingOutposts.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                    .putIfAbsent(saved.key(), new PendingOutpost(saved.key(), saved.box(), BlockPos.ZERO, Rotation.NONE));
        }

        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            if (start == null || !start.isValid()) continue;
            ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
            if (!STRUCTURE_ID.equals(id)) continue;
            BoundingBox box = start.getBoundingBox();
            String key = structureKey(level, box);
            BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
            data.confirmNaturalStructure(key);
            if (data.isLegacySkipped(key) || data.contains(key) && data.plan(key) == null) {
                // A confirmed start whose initialization already ended can still be cleared.
                data.registerStructure(key, level, box, (box.minY() + box.maxY()) / 2);
                data.markNaturalSpawnFinished(key);
                continue;
            }
            if (data.plan(key) != null && !data.plan(key).needsInitialization()) continue;
            PoolElementStructurePiece piece = start.getPieces().stream()
                    .filter(candidate -> candidate instanceof PoolElementStructurePiece)
                    .map(candidate -> (PoolElementStructurePiece) candidate).findFirst().orElse(null);
            if (piece == null) {
                CallResponseMod.LOGGER.error("复仇女仆据点 {} 没有可用的结构模板片段", key);
                data.registerStructure(key, level, box, (box.minY() + box.maxY()) / 2);
                data.markNaturalSpawnFinished(key);
                continue;
            }
            PendingOutpost natural = new PendingOutpost(key, box, piece.getPosition(), piece.getRotation());
            natural.natural = true;
            pendingOutposts.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                    .putIfAbsent(key, natural);
        }
    }

    private void initializeOneReadyOutpost(ServerLevel level) {
        initializeOneReadyOutpost(level, null);
    }

    private void initializeOneReadyOutpost(ServerLevel level, String preferredKey) {
        Map<String, PendingOutpost> pending = pendingOutposts.get(level.dimension());
        if (pending == null || pending.isEmpty()) return;
        BetrayalOutpostSavedData savedData = BetrayalOutpostSavedData.get(level);
        PendingOutpost outpost = pending.values().stream()
                .filter(candidate -> candidate.nextAttempt <= level.getGameTime()
                        && (preferredKey == null || preferredKey.equals(candidate.key))
                        && allChunksLoaded(level, candidate.box))
                .findFirst().orElse(null);
        if (outpost == null) return;
        outpost.nextAttempt = level.getGameTime() + RETRY_TICKS;
        try {
            var existing = savedData.plan(outpost.key);
            if (existing != null && !existing.needsSpawnWork() && existing.needsStoryWork()) {
                placeStoryBooks(level, savedData, outpost.key);
                if (!existing.needsStoryWork()) pending.remove(outpost.key);
                return;
            }
            if (outpost.layout == null) {
                var plan = savedData.plan(outpost.key);
                outpost.layout = plan != null && !plan.markerLayout().isEmpty()
                        ? decodeMarkerLayout(plan.markerLayout())
                        : plan != null ? readLayout(level) : scanMarkerLayout(level, outpost.box);
            }
            if (outpost.layout == null) {
                InitializationPlan nativeLoot = scanAndValidate(level, outpost);
                savedData.registerStructure(outpost.key, level, outpost.box, (outpost.box.minY() + outpost.box.maxY()) / 2);
                applyContainerLoot(level, outpost.key, nativeLoot.containerLoot());
                if (outpost.natural) savedData.markNaturalSpawnFinished(outpost.key);
                pending.remove(outpost.key);
                return;
            }
            CampLayout layout = outpost.layout;
            BlockPos home = worldPos(outpost, layout.center());
            if (!savedData.contains(outpost.key)) {
                // Register the real StructureStart and its native loot independently of maid success.
                ServerPlayer firstPlayer = nearestPlayer(level, outpost.box);
                savedData.createPlan(outpost.key, newSpawnPlan(level, outpost.key, layout, outpost.forceGly),
                        firstPlayer == null ? null : firstPlayer.getUUID(), outpost.natural);
                for (ContainerInfo chest : layout.containers()) {
                    if (STORY_TABLE.equals(chest.table())) {
                        BlockPos pos = worldPos(outpost, chest.pos());
                        savedData.setStoryChest(savedData.plan(outpost.key), pos, CampStoryBooks.registerChoice(level, pos, STORY_TABLE));
                        break;
                    }
                }
                savedData.setMarkerLayout(outpost.key, encodeMarkerLayout(layout));
                savedData.markInitialized(outpost.key);
                savedData.registerStructure(outpost.key, level, outpost.box, home);
                CallResponseMod.LOGGER.info("Outpost registered: {}, {} spawn markers, {} native loot containers",
                        outpost.key, layout.spawnCandidates().size(), layout.containers().size());
            }
            // A reload between registration and completion resumes only unfinished slots.
            if (savedData.plan(outpost.key) == null) {
                pending.remove(outpost.key);
                return;
            }
            savedData.markInitialized(outpost.key);
            savedData.registerStructure(outpost.key, level, outpost.box, home);
            registerMissingContainers(level, savedData, outpost, layout);
            for (BlockPos marker : layout.markers()) {
                if (level.getBlockEntity(marker) instanceof OutpostMarkerBlockEntity) {
                    level.setBlock(marker, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            spawnMissingMaids(level, savedData, outpost, layout, home);
            placeStoryBooks(level, savedData, outpost.key);
            settleNaturalCamp(level, savedData, outpost.key);
            personalizeMaids(level, savedData, outpost.key, outpost.box);
            if (!savedData.plan(outpost.key).needsInitialization()) pending.remove(outpost.key);
        } catch (RuntimeException exception) {
            if (++outpost.failures >= MAX_SPAWN_ATTEMPTS) {
                pending.remove(outpost.key);
                savedData.skipLegacy(outpost.key);
                var plan = savedData.plan(outpost.key);
                if (outpost.natural || plan != null && plan.natural()) {
                    savedData.registerStructure(outpost.key, level, outpost.box, (outpost.box.minY() + outpost.box.maxY()) / 2);
                    savedData.markNaturalSpawnFinished(outpost.key);
                }
            }
            CallResponseMod.LOGGER.error("Outpost initialization failed ({}/{}): {}",
                    outpost.failures, MAX_SPAWN_ATTEMPTS, outpost.key, exception);
        }
    }



    private static InitializationPlan scanAndValidate(ServerLevel level, PendingOutpost outpost) {
        Map<BlockPos, ResourceKey<LootTable>> containers = new HashMap<>();
        BoundingBox box = outpost.box();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.hasBlockEntity()) {
                        BlockEntity blockEntity = level.getBlockEntity(pos);
                        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
                            ResourceKey<LootTable> loot = container.getLootTable();
                            if (loot != null && OUTPOST_LOOT.contains(loot)) {
                                containers.put(pos.immutable(), loot);
                            }
                        }
                    }
                }
            }
        }

        List<OutpostMarkerBlockEntity> markers = collectMarkers(level, box);
        markers.removeIf(OutpostMarkerBlockEntity::isWanderingCamp);
        long anchors = markers.stream().filter(OutpostMarkerBlockEntity::isAnchor).count();
        List<MarkerSpawn> spawns = toSpawns(markers);
        long fixedSpawns = spawns.stream().filter(spawn -> spawn.role().toMaidRole() != null).count();
        boolean valid = anchors == 1 && spawns.size() >= MAID_COUNT && fixedSpawns <= MAID_COUNT;
        if (!valid) {
            CallResponseMod.LOGGER.error("据点 {} 的标记不合格：锚点需要 1 个（实际 {}），出生标记至少需要 {} 个（实际 {}），固定角色标记最多 {} 个（实际 {}）；标记保留以便检查",
                    outpost.key(), anchors, MAID_COUNT, spawns.size(), MAID_COUNT, fixedSpawns);
        }
        return new InitializationPlan(containers, spawns,
                markers.stream().map(marker -> marker.getBlockPos().immutable()).toList(), valid,
                markers.stream().filter(OutpostMarkerBlockEntity::isAnchor).map(OutpostMarkerBlockEntity::getBlockPos).findFirst().orElse(null));
    }

    private static void applyContainerLoot(ServerLevel level, String campKey,
                                           Map<BlockPos, ResourceKey<LootTable>> boundContainers) {
        BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
        for (Map.Entry<BlockPos, ResourceKey<LootTable>> entry : boundContainers.entrySet()) {
            BlockEntity blockEntity = level.getBlockEntity(entry.getKey());
            if (blockEntity instanceof RandomizableContainerBlockEntity container
                    && entry.getValue().equals(container.getLootTable())) {
                BlockPos pos = entry.getKey();
                BlockPos otherHalf = null;
                BlockState state = container.getBlockState();
                if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                    BlockPos partner = pos.relative(ChestBlock.getConnectedDirection(state));
                    if (level.getBlockEntity(partner) instanceof RandomizableContainerBlockEntity companion
                            && companion.getLootTable() == null) otherHalf = partner;
                }
                data.registerLootChest(level, campKey, pos, otherHalf,
                        container.getLootTable().location(), container.getLootTableSeed());
            }
        }
    }

    private static void removeDevelopmentMarkers(ServerLevel level, InitializationPlan plan) {
        plan.markers().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
    }

    private static List<EntityMaid> createMaidGroup(ServerLevel level, List<MarkerSpawn> spawns,
                                                     String group, BlockPos home, ServerPlayer skinOwner,
                                                     boolean forceGly) {
        // 带角色的标记固定占位，role=none 的标记作为候选点补齐剩余名额。
        List<MarkerSpawn> fixed = new ArrayList<>();
        List<MarkerSpawn> free = new ArrayList<>();
        for (MarkerSpawn spawn : spawns) {
            if (spawn.role().toMaidRole() == null) {
                free.add(spawn);
            } else {
                fixed.add(spawn);
            }
        }
        Collections.shuffle(free, new java.util.Random(level.getRandom().nextLong()));
        int needed = MAID_COUNT - fixed.size();
        if (needed < 0 || free.size() < needed) return List.of();

        List<MarkerSpawn> chosen = new ArrayList<>(fixed);
        chosen.addAll(free.subList(0, needed));
        Collections.shuffle(chosen, new java.util.Random(level.getRandom().nextLong()));

        List<BetrayalOutpostMaidData.Role> spare = new ArrayList<>(List.of(
                BetrayalOutpostMaidData.Role.HEAVY,
                BetrayalOutpostMaidData.Role.SWORDSMAN,
                BetrayalOutpostMaidData.Role.SWORDSMAN,
                BetrayalOutpostMaidData.Role.FARMER,
                BetrayalOutpostMaidData.Role.FEEDER));
        List<BetrayalOutpostMaidData.Role> roles = new ArrayList<>(MAID_COUNT);
        for (MarkerSpawn spawn : chosen) {
            BetrayalOutpostMaidData.Role role = spawn.role().toMaidRole();
            roles.add(role);
            if (role != null) spare.remove(role);
        }
        Collections.shuffle(spare, new java.util.Random(level.getRandom().nextLong()));
        int nextSpare = 0;
        for (int i = 0; i < roles.size(); i++) {
            if (roles.get(i) == null) {
                roles.set(i, nextSpare < spare.size()
                        ? spare.get(nextSpare++)
                        : BetrayalOutpostMaidData.Role.SWORDSMAN);
            }
        }
        // 1% 彩蛋：整座营地有概率出现一只只会说话的 gly；调试命令可以强制本次出现。
        if (!roles.contains(BetrayalOutpostMaidData.Role.GLY)
                && (forceGly || level.getRandom().nextFloat() < 0.01F)) {
            roles.set(level.getRandom().nextInt(roles.size()), BetrayalOutpostMaidData.Role.GLY);
        }

        List<EntityMaid> maids = new ArrayList<>(MAID_COUNT);
        for (int i = 0; i < MAID_COUNT; i++) {
            var maid = com.github.JumDa5he.callresponse.compat.outpost.entity.OutpostEntities.REVENGE_MAID.get().create(level);
            if (maid == null) return List.of();
            BlockPos pos = chosen.get(i).pos();
            maid.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    level.getRandom().nextFloat() * 360.0F, 0.0F);
            maid.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
            initializeMaid(maid, group, roles.get(i), home);
            if (skinOwner != null) {
                maid.setModelId(WanderingMaidManager.selectSharedModel(level, skinOwner.getUUID()));
                if (!BetrayalOutpostMaidData.isGly(maid)) applyRevengeHealth(maid,
                        Math.max(0, skinOwner.getStats().getValue(Stats.ENTITY_KILLED.get(EntityMaid.TYPE))));
                maid.setHealth(maid.getMaxHealth());
                maid.getPersistentData().putBoolean("CallResponseOutpostPersonalized", true);
            }
            maids.add(maid);
        }
        return maids;
    }

    /**
     * 调试命令：在指定位置放置 betrayal_maid_outpost，并立刻按模板里的
     * anchor / spawn marker 初始化女仆，同时把营地登记进 SavedData，
     * 让不祥之兆可以在调试营地里正常触发袭击。forceGly 为 true 时本次强制一只 GLY。
     */
    public static boolean debugGenerate(ServerLevel level, BlockPos origin, ServerPlayer player, boolean forceGly) {
        if (instance == null) return false;
        StructureTemplate template = level.getStructureManager().getOrCreate(
                ResourceLocation.fromNamespaceAndPath("callresponse", "betrayal_maid_outpost_final"));
        var size = template.getSize();
        BoundingBox box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX() - 1, origin.getY() + size.getY() - 1, origin.getZ() + size.getZ() - 1);
        String key = structureKey(level, box);
        if (BetrayalOutpostSavedData.get(level).contains(key)) return false;
        if (!template.placeInWorld(level, origin, origin,
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                level.getRandom(), Block.UPDATE_ALL)) return false;
        PendingOutpost pending = new PendingOutpost(key, box, origin, Rotation.NONE);
        pending.forceGly = forceGly;
        instance.pendingOutposts.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>()).put(key, pending);
        instance.initializeOneReadyOutpost(level, key);
        return BetrayalOutpostSavedData.get(level).plan(key) != null;
    }

    /** 击杀数直接写入真实 MAX_HEALTH，不再创建第二层伤害血池。 */
    private static void applyRevengeHealth(EntityMaid maid, int maidKills) {
        if (maidKills <= 0) return;
        AttributeInstance maxHealth = maid.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        double bonus = BetrayalOutpostMaxHealth.computeRevengeHealthBonus(maidKills, maid.getMaxHealth());
        maxHealth.setBaseValue(maxHealth.getBaseValue() + bonus);
        maid.setHealth(maid.getMaxHealth());
    }

    private static void setTaskForRole(EntityMaid maid, BetrayalOutpostMaidData.Role role) {
        if (role == BetrayalOutpostMaidData.Role.GLY) return;
        String path = switch (role) {
            case FARMER -> "farm";
            case FEEDER -> "feed";
            default -> "attack";
        };
        TaskManager.findTask(ResourceLocation.fromNamespaceAndPath("touhou_little_maid", path)).ifPresent(maid::setTask);
    }

    private static void equipForRole(EntityMaid maid, BetrayalOutpostMaidData.Role role, RandomSource random,
                                     HolderLookup.RegistryLookup<Enchantment> enchantments) {
        switch (role) {
            case HEAVY -> {
                maid.setItemSlot(EquipmentSlot.MAINHAND, enchanted(new ItemStack(random.nextFloat() < 0.3F ? Items.DIAMOND_AXE : Items.IRON_AXE), random, enchantments));
                maid.setItemSlot(EquipmentSlot.OFFHAND, enchanted(new ItemStack(Items.SHIELD), random, enchantments));
                maid.setItemSlot(EquipmentSlot.HEAD, enchanted(new ItemStack(Items.IRON_HELMET), random, enchantments));
                maid.setItemSlot(EquipmentSlot.CHEST, enchanted(new ItemStack(Items.DIAMOND_CHESTPLATE), random, enchantments));
                maid.setItemSlot(EquipmentSlot.LEGS, enchanted(new ItemStack(Items.IRON_LEGGINGS), random, enchantments));
                maid.setItemSlot(EquipmentSlot.FEET, enchanted(new ItemStack(Items.DIAMOND_BOOTS), random, enchantments));
                reduceMovementSpeed(maid, 0.7D);
            }
            case SWORDSMAN -> {
                maid.setItemSlot(EquipmentSlot.MAINHAND, enchanted(new ItemStack(random.nextFloat() < 0.25F ? Items.DIAMOND_SWORD : Items.IRON_SWORD), random, enchantments));
                maid.setItemSlot(EquipmentSlot.HEAD, enchanted(new ItemStack(Items.IRON_HELMET), random, enchantments));
                maid.setItemSlot(EquipmentSlot.CHEST, enchanted(new ItemStack(Items.IRON_CHESTPLATE), random, enchantments));
                maid.setItemSlot(EquipmentSlot.LEGS, enchanted(new ItemStack(Items.IRON_LEGGINGS), random, enchantments));
                maid.setItemSlot(EquipmentSlot.FEET, enchanted(new ItemStack(Items.IRON_BOOTS), random, enchantments));
                reduceMovementSpeed(maid, 0.9D);
            }
            case FARMER, FEEDER -> {
                maid.setItemSlot(EquipmentSlot.MAINHAND, enchanted(new ItemStack(role == BetrayalOutpostMaidData.Role.FARMER ? Items.IRON_HOE : Items.IRON_SHOVEL), random, enchantments));
                maid.setItemSlot(EquipmentSlot.HEAD, enchanted(new ItemStack(Items.LEATHER_HELMET), random, enchantments));
                maid.setItemSlot(EquipmentSlot.CHEST, enchanted(new ItemStack(Items.IRON_CHESTPLATE), random, enchantments));
                maid.setItemSlot(EquipmentSlot.LEGS, enchanted(new ItemStack(Items.LEATHER_LEGGINGS), random, enchantments));
                maid.setItemSlot(EquipmentSlot.FEET, enchanted(new ItemStack(Items.IRON_BOOTS), random, enchantments));
                reduceMovementSpeed(maid, 0.9D);
            }
            case GLY -> {
                giveGlyBoombox(maid);
                addGlySign(maid, false);
            }
        }
    }

    /** 安装了 IAMMusicPlayer 时让彩蛋女仆拿着 boombox；未安装则保持空手。 */
    private static void giveGlyBoombox(EntityMaid maid) {
        ResourceLocation boomboxId = ResourceLocation.fromNamespaceAndPath("iammusicplayer", "boombox");
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(boomboxId)) return;
        ItemStack boombox = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(boomboxId));
        if (!boombox.isEmpty()) {
            maid.setItemSlot(EquipmentSlot.MAINHAND, boombox);
        }
    }

    private static final Component[] glyTexts = new Component[]{Component.empty(),
            Component.translatable("message.callresponse.outpost.sign.gly.1"),
            Component.translatable("message.callresponse.outpost.sign.gly.2"),
            Component.empty()
    };
    private static final SignText glySignText = new SignText(glyTexts, glyTexts.clone(), DyeColor.CYAN, true);

    public static void addGlySign(EntityMaid maid, boolean scareOnlookers) {
        MaidSignManager.attach(maid, Items.OAK_SIGN, scareOnlookers);
        MaidSignManager.setText(maid, glySignText);
    }

    private static void reduceMovementSpeed(EntityMaid maid, double multiplier) {
        AttributeInstance movement = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(movement.getBaseValue() * multiplier);
    }

    private static ItemStack enchanted(ItemStack stack, RandomSource random,
                                       HolderLookup.RegistryLookup<Enchantment> enchantments) {
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
            // 据点的主战武器必定附魔，且锋利至少为 II。
            stack.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 2 + random.nextInt(2));
        } else if (random.nextFloat() >= 0.8F) {
            return stack;
        } else if (stack.getItem() instanceof ArmorItem) {
            stack.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 1 + random.nextInt(3));
        } else if (stack.getItem() instanceof HoeItem || stack.getItem() instanceof ShovelItem) {
            stack.enchant(enchantments.getOrThrow(Enchantments.EFFICIENCY), 1 + random.nextInt(3));
        } else {
            stack.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 1 + random.nextInt(2));
        }
        return stack;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void coordinateOutpostStacking(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof EntityMaid surrendered
                && com.github.JumDa5he.callresponse.compat.winefox.WineFoxSurrender.isSurrendered(surrendered)) return;
        if (event.getEntity() instanceof com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity) return;
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide
                && BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            BetrayalOutpostMaidData.ensureUntamed(maid);
            BetrayalOutpostMaidData.migrateLegacyExtraHealth(maid);
            BetrayalOutpostMaidData.updateLegacyMovementSpeed(maid);
            BetrayalOutpostMaidData.ensureCampSchedule(maid);
            if (BetrayalOutpostMaidData.isGly(maid)) {
                maid.setTarget(null);
                maid.setAggressive(false);
                maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
                BetrayalOutpostMaidData.tickGlyDialogue(maid);
                BetrayalOutpostMaidData.tickGlyBehavior(maid);
                return;
            }
            BetrayalOutpostAlertManager.tick(maid);
            EmotionBetrayalManager.tickOutpostCombat(maid);
            BetrayalOutpostStackManager.tick(maid);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void preventSisterDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid victim)
                || !BetrayalOutpostMaidData.isOutpostMaid(victim)) return;

        Entity attacker = com.github.JumDa5he.callresponse.compat.winefox.WineFoxFaction.resolveAttacker(event.getSource());
        if (com.github.JumDa5he.callresponse.compat.winefox.WineFoxFaction.blocksAttack(attacker, victim)) {
            if (attacker instanceof EntityMaid attackingMaid)
                BetrayalOutpostAlertManager.clearFriendlyTargets(attackingMaid);
            event.setCanceled(true);
            return;
        }
        if (attacker instanceof EntityMaid attackingMaid
                && BetrayalOutpostMaidData.areSisters(attackingMaid, victim)) {
            attackingMaid.setTarget(null);
            event.setCanceled(true);
            return;
        }
        if (attacker != null) BetrayalOutpostMaidData.markProvoked(victim);
    }

    /** GLY 只说话不还手；受到有来源的攻击时从 hurt_lines 里说一句。 */
    public static void onGlyHurt(EntityMaid maid, DamageSource source) {
        if (!BetrayalOutpostMaidData.isOutpostMaid(maid) || !BetrayalOutpostMaidData.isGly(maid)
                || com.github.JumDa5he.callresponse.compat.winefox.WineFoxSurrender.isSurrendered(maid)
                || source.is(DamageTypes.STARVE)) return;
        Entity attacker = source.getEntity();
        if (attacker == null) attacker = source.getDirectEntity();
        if (attacker == null) return;
        BetrayalOutpostMaidData.tickGlyHurtDialogue(maid);
        BetrayalOutpostMaidData.startGlyFlee(maid, attacker);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void preventGlyCombat(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && BetrayalOutpostMaidData.isOutpostMaid(maid)
                && BetrayalOutpostMaidData.isGly(maid)) event.setNewAboutToBeSetTarget(null);
    }

    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)
                || !BetrayalOutpostMaidData.isOutpostMaid(maid)
                || !(maid.level() instanceof ServerLevel level)) return;

        sendDeathDialogue(level, maid);

        // TLM 的野生女仆不会走有主女仆的墓碑掉落，因此在这里单独且仅一次转移穿戴装备。
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = maid.getItemBySlot(slot);
            if (equipped.isEmpty()) continue;
            addDrop(event, level, maid, equipped.copy());
            maid.setItemSlot(slot, ItemStack.EMPTY);
        }

        // Untamed camp maids do not get TLM's normal tombstone inventory transfer.
        var inventory = maid.getMaidInv();
        int availableSlots = Math.min(inventory.getSlots(), maid.getMaidBackpackType().getAvailableMaxContainerIndex());
        for (int slot = 0; slot < availableSlots; slot++) {
            ItemStack stored = inventory.getStackInSlot(slot);
            if (stored.isEmpty()) continue;
            addDrop(event, level, maid, stored.copy());
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }

        RandomSource random = level.getRandom();
        switch (BetrayalOutpostMaidData.role(maid)) {
            case SWORDSMAN -> {
                if (random.nextFloat() < 0.35F) addDrop(event, level, maid, new ItemStack(Items.DIAMOND, 2));
                addDrop(event, level, maid, new ItemStack(Items.IRON_INGOT, 1 + random.nextInt(3)));
                if (random.nextBoolean()) addDrop(event, level, maid, new ItemStack(Items.EMERALD, 1 + random.nextInt(2)));
            }
            case FARMER, FEEDER -> {
                ItemStack food = switch (random.nextInt(4)) {
                    case 0 -> new ItemStack(Items.WHEAT, 4 + random.nextInt(6));
                    case 1 -> new ItemStack(Items.CARROT, 4 + random.nextInt(6));
                    case 2 -> new ItemStack(Items.POTATO, 4 + random.nextInt(6));
                    default -> new ItemStack(Items.BREAD, 2 + random.nextInt(4));
                };
                addDrop(event, level, maid, food);
            }
            default -> {
            }
        }
    }

    private static void sendDeathDialogue(ServerLevel level, EntityMaid maid) {
        Component modelName = maid.hasCustomName() ? maid.getCustomName().copy()
                : ServerCustomPackLoader.SERVER_MAID_MODELS.getInfo(maid.getModelId())
                .map(info -> modelNameWithFallback(ParseI18n.parse(info.getName()), maid.getType().getDescription()))
                .orElse(maid.getType().getDescription());
        Component message = Component.translatable("message.callresponse.outpost.death.format",
                modelName, deathLine(level, maid));
        double rangeSqr = 64.0D * 64.0D;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(maid) <= rangeSqr) player.sendSystemMessage(message);
        }
    }

    /** GLY 优先使用资源台词，空池回退普通营地死亡台词。 */
    private static Component deathLine(ServerLevel level, EntityMaid maid) {
        if (BetrayalOutpostMaidData.isGly(maid)) {
            List<String> pool = OutpostGlyDialogue.deathLines();
            if (!pool.isEmpty()) return Component.literal(pool.get(level.getRandom().nextInt(pool.size())));
        }
        return Component.translatable("message.callresponse.outpost.death." + (1 + level.getRandom().nextInt(10)));
    }

    // 保留翻译及参数交给接收客户端；缺少模型语言资源时回退到实体名称。
    private static Component modelNameWithFallback(Component name, Component fallback) {
        net.minecraft.network.chat.MutableComponent result;
        if (name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text) {
            Object[] args = java.util.Arrays.copyOf(text.getArgs(), text.getArgs().length + 1);
            args[args.length - 1] = fallback;
            result = Component.translatableWithFallback(text.getKey(), "%" + args.length + "$s", args);
        } else {
            result = net.minecraft.network.chat.MutableComponent.create(name.getContents());
        }
        result.setStyle(name.getStyle());
        name.getSiblings().forEach(sibling -> result.append(modelNameWithFallback(sibling, fallback)));
        return result;
    }

    private static void addDrop(LivingDropsEvent event, ServerLevel level, EntityMaid maid, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, maid.getX(), maid.getY() + 0.35D, maid.getZ(), stack);
        item.setDefaultPickUpDelay();
        event.getDrops().add(item);
    }

    private static boolean allChunksLoaded(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, BoundingBox box) {
        return nearestPlayerWithin(level, box, Double.POSITIVE_INFINITY);
    }

    private static String structureKey(ServerLevel level, BoundingBox box) {
        return level.dimension().location() + "|" + STRUCTURE_ID + "|"
                + box.minX() + "," + box.minY() + "," + box.minZ();
    }

    /** 只遍历已加载区块的 BlockEntity，不再按方块类型扫描整个结构体积。 */
    private static List<OutpostMarkerBlockEntity> collectMarkers(ServerLevel level, BoundingBox box) {
        List<OutpostMarkerBlockEntity> markers = new ArrayList<>();
        for (int chunkX = box.minX() >> 4; chunkX <= (box.maxX() >> 4); chunkX++) {
            for (int chunkZ = box.minZ() >> 4; chunkZ <= (box.maxZ() >> 4); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof OutpostMarkerBlockEntity marker)) continue;
                    if (box.isInside(marker.getBlockPos())) {
                        markers.add(marker);
                    }
                }
            }
        }
        return markers;
    }

    private static List<MarkerSpawn> toSpawns(List<OutpostMarkerBlockEntity> markers) {
        return markers.stream()
                .filter(marker -> !marker.isAnchor())
                .map(marker -> new MarkerSpawn(marker.getBlockPos().immutable(), marker.spawnRole()))
                .toList();
    }

    /** 一个出生点标记：位置 + 模板里写死的角色（NONE 表示运行时随机分配）。 */
    private record MarkerSpawn(BlockPos pos, OutpostMarkerRole role) {
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) return;
        deliverClearRewards(player);
        ServerLevel level = player.serverLevel();
        BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
        BetrayalOutpostSavedData.Outpost camp = data.findAt(level, player.blockPosition());
        if (camp != null && data.plan(camp.key()) != null && data.plan(camp.key()).needsPersonalization()) {
            personalizeMaids(level, data, camp.key(), camp.box());
        }
    }

    private static List<BetrayalOutpostSavedData.SpawnSlot> newSpawnPlan(ServerLevel level, String key, CampLayout layout, boolean forceGly) {
        List<Integer> shuffled = new ArrayList<>();
        List<Integer> fixed = new ArrayList<>();
        for (int i = 0; i < layout.spawnCandidates().size(); i++) {
            if (layout.roles().get(i) == OutpostMarkerRole.NONE) shuffled.add(i);
            else fixed.add(i);
        }
        Collections.shuffle(shuffled, new java.util.Random(level.getRandom().nextLong()));
        if (fixed.size() > 5 || fixed.size() + shuffled.size() < 5) {
            throw new IllegalStateException("Camp markers cannot supply exactly five roles");
        }
        fixed.addAll(shuffled.subList(0, 5 - fixed.size()));
        shuffled = fixed;
        List<BetrayalOutpostMaidData.Role> roles = new ArrayList<>(List.of(
                BetrayalOutpostMaidData.Role.HEAVY,
                BetrayalOutpostMaidData.Role.SWORDSMAN,
                BetrayalOutpostMaidData.Role.SWORDSMAN,
                BetrayalOutpostMaidData.Role.FARMER,
                BetrayalOutpostMaidData.Role.FEEDER));
        List<BetrayalOutpostMaidData.Role> assigned = new ArrayList<>();
        for (int index : shuffled) {
            var role = layout.roles().get(index).toMaidRole();
            assigned.add(role);
            if (role != null) roles.remove(role);
        }
        Collections.shuffle(roles, new java.util.Random(level.getRandom().nextLong()));
        int spare = 0;
        for (int i = 0; i < assigned.size(); i++) {
            if (assigned.get(i) == null) assigned.set(i, roles.get(spare++));
        }
        if (!assigned.contains(BetrayalOutpostMaidData.Role.GLY)
                && (forceGly || level.getRandom().nextFloat() < 0.01F)) {
            assigned.set(level.getRandom().nextInt(5), BetrayalOutpostMaidData.Role.GLY);
        }
        List<BetrayalOutpostSavedData.SpawnSlot> slots = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.nameUUIDFromBytes((key + "|revenge-maid|" + i).getBytes(StandardCharsets.UTF_8));
            slots.add(new BetrayalOutpostSavedData.SpawnSlot(shuffled.get(i), assigned.get(i).name(), id, 0, false, false));
        }
        return slots;
    }

    private static void registerMissingContainers(ServerLevel level, BetrayalOutpostSavedData data,
                                                  PendingOutpost outpost, CampLayout layout) {
        for (ContainerInfo container : layout.containers()) {
            data.registerLootChest(level, outpost.key, worldPos(outpost, container.pos()),
                    container.otherHalf() == null ? null : worldPos(outpost, container.otherHalf()),
                    container.table(), container.seed());
        }
    }

    private static void spawnMissingMaids(ServerLevel level, BetrayalOutpostSavedData data,
                                          PendingOutpost outpost, CampLayout layout, BlockPos home) {
        List<BetrayalOutpostSavedData.SpawnSlot> slots = data.plan(outpost.key).slots();
        for (int i = 0; i < slots.size(); i++) {
            BetrayalOutpostSavedData.SpawnSlot slot = slots.get(i);
            if (slot.spawned() || slot.attempts() >= MAX_SPAWN_ATTEMPTS) continue;
            Entity existing = level.getEntity(slot.entityId());
            if (existing instanceof EntityMaid maid && BetrayalOutpostMaidData.isOutpostMaid(maid)
                    && outpost.key.equals(BetrayalOutpostMaidData.group(maid))) {
                if (BetrayalOutpostMaidData.isGly(maid)) data.confirmStoryGly(data.plan(outpost.key));
                data.updateSlot(outpost.key, i, new BetrayalOutpostSavedData.SpawnSlot(
                        slot.candidate(), slot.role(), slot.entityId(), slot.attempts(), true, false));
                continue;
            }
            BlockPos pos = worldPos(outpost, layout.spawnCandidates().get(slot.candidate()));
            if (existing != null || !level.getBlockState(pos).isAir()
                    || !level.getBlockState(pos.above()).isAir()
                    || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
                recordSpawnFailure(data, outpost.key, i, slot, "出生位置被占用或没有安全地面：" + pos);
                continue;
            }
            try {
                EntityMaid maid = createOutpostMaid(level, pos, home, outpost.key, slot);
                if (maid instanceof com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity revenge)
                    revenge.rememberCampEntrance(outpost.box, home);
                if (maid == null || !level.addFreshEntity(maid)) {
                    recordSpawnFailure(data, outpost.key, i, slot, "女仆未能加入世界：" + pos);
                    continue;
                }
                // A slot becomes complete only after the real entity successfully enters the level.
                if (BetrayalOutpostMaidData.isGly(maid)) data.confirmStoryGly(data.plan(outpost.key));
                data.updateSlot(outpost.key, i, new BetrayalOutpostSavedData.SpawnSlot(
                        slot.candidate(), slot.role(), slot.entityId(), slot.attempts(), true, false));
            } catch (RuntimeException exception) {
                recordSpawnFailure(data, outpost.key, i, slot, "实体初始化异常：" + pos);
                CallResponseMod.LOGGER.error("复仇女仆据点 {} 第 {} 个出生位异常", outpost.key, i + 1, exception);
            }
        }
    }

    public static boolean useRecruitedStory(ServerLevel level, BlockPos pos) {
        var plan = BetrayalOutpostSavedData.get(level).storyPlanAt(level, pos, STORY_TABLE);
        return plan == null ? CampStoryBooks.selectNew(level, pos) : plan.recruitedStory();
    }

    public static boolean hasStoryGly(ServerLevel level, BlockPos pos) {
        var plan = BetrayalOutpostSavedData.get(level).storyPlanAt(level, pos, STORY_TABLE);
        return plan != null && plan.hasStoryGly();
    }

    public static boolean storyPending(ServerLevel level, BlockPos pos) {
        var data = BetrayalOutpostSavedData.get(level);
        var plan = data.storyPlanAt(level, pos, STORY_TABLE);
        if (plan != null) return plan.storyGlyPending();
        // A first personal opening can arrive before the queued StructureStart is processed.
        var pending = instance == null ? null : instance.pendingOutposts.get(level.dimension());
        if (pending != null && pending.values().stream()
                .anyMatch(camp -> camp.box.isInside(pos) && data.plan(camp.key) == null)) return true;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE_ID);
        if (chunk == null || structure == null) return false;
        if (awaitingStoryRegistration(level, data, pos, chunk.getStartForStructure(structure))) return true;
        for (long packed : chunk.getReferencesForStructure(structure)) {
            LevelChunk startChunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            if (startChunk == null || awaitingStoryRegistration(level, data, pos, startChunk.getStartForStructure(structure)))
                return true;
        }
        return false;
    }

    private static boolean awaitingStoryRegistration(ServerLevel level, BetrayalOutpostSavedData data,
                                                       BlockPos pos, StructureStart start) {
        if (start == null || !start.isValid() || !start.getBoundingBox().isInside(pos)) return false;
        String camp = structureKey(level, start.getBoundingBox());
        return data.plan(camp) == null && !data.isLegacySkipped(camp);
    }

    /** Only a physical vanilla fill writes this receipt; personal rolls never settle another inventory. */
    public static void markTableGly(ServerLevel level, RandomizableContainerBlockEntity chest) {
        var data = BetrayalOutpostSavedData.get(level);
        var plan = data.storyPlanAt(level, chest.getBlockPos(), STORY_TABLE);
        if (plan == null || !plan.hasStoryGly()) return;
        chest.getPersistentData().putUUID(GLY_BOOK_RECEIPT, plan.instanceId());
        chest.setChanged();
        data.markStoryIssued(plan, true);
    }

    private static void placeStoryBooks(ServerLevel level, BetrayalOutpostSavedData data, String camp) {
        var plan = data.plan(camp);
        if (!plan.needsStoryWork()) return;
        BlockPos pos = plan.storyChest();
        var binding = data.lootChest(level, pos);
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)) {
            data.retireStoryChest(plan);
            return;
        }
        boolean bound = binding != null && camp.equals(binding.campKey()) && STORY_TABLE.equals(binding.lootTable());
        var table = chest.getLootTable();
        boolean personal = CampStoryBooks.isPersonalChest(chest);
        if ((table != null && STORY_TABLE.equals(table.location()) && (bound || personal && binding == null))
                || (table == null && personal && bound)) {
            // The table owns unopened chests, including each independent personal roll.
            // Clearing the pending table here would prevent later conversion by Lootr.
            data.markStoryIssued(plan, false);
            if (plan.hasStoryGly()) data.markStoryIssued(plan, true);
            return;
        }
        if (!bound || table != null || personal) {
            data.retireStoryChest(plan);
            return;
        }
        CompoundTag chestData = chest.getPersistentData();
        if (!STORY_TABLE.toString().equals(chestData.getString(CampStoryBooks.TABLE_RECEIPT))) {
            // Coordinates alone are insufficient: a player may have replaced the original chest.
            data.retireStoryChest(plan);
            return;
        }
        // The native story table supplies exactly one regular book, including if a hopper or
        // player already unpacked it. Never replace an item the player took with a second book.
        if (!plan.normalStoryIssued()) data.markStoryIssued(plan, false);
        if (!plan.needsStoryWork()) return;
        if (chestData.hasUUID(GLY_BOOK_RECEIPT) && plan.instanceId().equals(chestData.getUUID(GLY_BOOK_RECEIPT))) {
            data.markStoryIssued(plan, true);
            return;
        }
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (!chest.getItem(slot).isEmpty()) continue;
            chest.setItem(slot, CampStoryBooks.OUTPOST_GLY.create());
            chestData.putUUID(GLY_BOOK_RECEIPT, plan.instanceId());
            chest.setChanged();
            data.markStoryIssued(plan, true);
            return;
        }
        // A full story chest keeps its pending receipt until a slot is free.
    }

    private static void recordSpawnFailure(BetrayalOutpostSavedData data, String key, int index,
                                           BetrayalOutpostSavedData.SpawnSlot slot, String reason) {
        int attempts = slot.attempts() + 1;
        data.updateSlot(key, index, new BetrayalOutpostSavedData.SpawnSlot(
                slot.candidate(), slot.role(), slot.entityId(), attempts, false, false));
        CallResponseMod.LOGGER.error("复仇女仆据点 {} 第 {} 个出生位失败（{}/{}）：{}",
                key, index + 1, attempts, MAX_SPAWN_ATTEMPTS, reason);
    }

    private static EntityMaid createOutpostMaid(ServerLevel level, BlockPos pos, BlockPos home,
                                                 String group, BetrayalOutpostSavedData.SpawnSlot slot) {
        var maid = com.github.JumDa5he.callresponse.compat.outpost.entity.OutpostEntities.REVENGE_MAID.get().create(level);
        if (maid == null) return null;
        maid.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        maid.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        maid.setUUID(slot.entityId());
        initializeMaid(maid, group, BetrayalOutpostMaidData.Role.valueOf(slot.role()), home);
        return maid;
    }


    private static void personalizeMaids(ServerLevel level, BetrayalOutpostSavedData data,
                                         String campKey, BoundingBox box) {
        BetrayalOutpostSavedData.CampPlan plan = data.plan(campKey);
        if (plan.referencePlayer() == null) {
            ServerPlayer nearby = nearestPlayerWithin(level, box, 64.0D);
            if (nearby == null) return;
            data.setReferencePlayer(campKey, nearby.getUUID());
        }
        ServerPlayer reference = level.getServer().getPlayerList().getPlayer(plan.referencePlayer());
        if (reference == null) return;
        int maidKills = Math.max(0, reference.getStats().getValue(Stats.ENTITY_KILLED.get(EntityMaid.TYPE)));
        List<BetrayalOutpostSavedData.SpawnSlot> slots = plan.slots();
        for (int i = 0; i < slots.size(); i++) {
            BetrayalOutpostSavedData.SpawnSlot slot = slots.get(i);
            if (!slot.spawned() || slot.personalized()) continue;
            Entity entity = level.getEntity(slot.entityId());
            if (entity instanceof EntityMaid maid && BetrayalOutpostMaidData.isOutpostMaid(maid)
                    && campKey.equals(BetrayalOutpostMaidData.group(maid))) {
                if (!maid.getPersistentData().getBoolean("CallResponseOutpostPersonalized")) {
                    maid.setModelId(WanderingMaidManager.selectSharedModel(level, reference.getUUID()));
                    if (!BetrayalOutpostMaidData.isGly(maid)) applyRevengeHealth(maid, maidKills);
                    maid.setHealth(maid.getMaxHealth());
                    maid.getPersistentData().putBoolean("CallResponseOutpostPersonalized", true);
                }
            }
            // A missing entity may already have been killed. Never recreate it for personalization.
            data.updateSlot(campKey, i, new BetrayalOutpostSavedData.SpawnSlot(
                    slot.candidate(), slot.role(), slot.entityId(), slot.attempts(), true, true));
        }
    }

    private static CampLayout readLayout(ServerLevel level) {
        try (Reader reader = new InputStreamReader(level.getServer().getResourceManager()
                .getResource(LAYOUT_ID).orElseThrow(() -> new IOException("Missing " + LAYOUT_ID))
                .open(), StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            BlockPos center = parsePos(object.getAsJsonArray("center"));
            List<BlockPos> candidates = new ArrayList<>();
            for (var point : object.getAsJsonArray("spawnCandidates")) {
                candidates.add(parsePos(point.getAsJsonArray()));
            }
            List<ContainerInfo> containers = new ArrayList<>();
            for (var entry : object.getAsJsonArray("containers")) {
                JsonObject container = entry.getAsJsonObject();
                BlockPos other = container.get("otherHalf").isJsonNull() ? null
                        : parsePos(container.getAsJsonArray("otherHalf"));
                containers.add(new ContainerInfo(parsePos(container.getAsJsonArray("pos")), other,
                        ResourceLocation.parse(container.get("table").getAsString()), 0L));
            }
            if (candidates.size() != 10 || containers.size() != 15) {
                throw new IllegalStateException("Unexpected camp layout size");
            }
            return new CampLayout(center, List.copyOf(candidates), List.copyOf(containers),
                    Collections.nCopies(candidates.size(), OutpostMarkerRole.NONE), List.of(), false);
        } catch (IOException | RuntimeException exception) {
            CallResponseMod.LOGGER.error("复仇女仆营地布局资源无法加载：{}", LAYOUT_ID, exception);
            return null;
        }
    }

    private static BlockPos parsePos(JsonArray array) {
        if (array.size() != 3) throw new IllegalStateException("Expected XYZ in camp layout");
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    private static BlockPos worldPos(PendingOutpost outpost, BlockPos local) {
        if (outpost.layout != null && outpost.layout.worldCoordinates()) return local;
        return outpost.origin.offset(StructureTemplate.transform(local, Mirror.NONE, outpost.rotation, BlockPos.ZERO));
    }

    /** Only loaded block entities: no volume scan, ore scan or forced chunk load. */
    private static CampLayout scanMarkerLayout(ServerLevel level, BoundingBox box) {
        List<OutpostMarkerBlockEntity> markers = new ArrayList<>();
        List<ContainerInfo> containers = new ArrayList<>();
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos pos = entity.getBlockPos();
                    if (!box.isInside(pos)) continue;
                    if (entity instanceof OutpostMarkerBlockEntity marker) {
                        markers.add(marker);
                    } else if (entity instanceof RandomizableContainerBlockEntity chest) {
                        CompoundTag nbt = entity.saveWithoutMetadata(level.registryAccess());
                        var pendingTable = chest.getLootTable();
                        ResourceLocation table = pendingTable == null
                                ? ResourceLocation.tryParse(nbt.getString("LootTable")) : pendingTable.location();
                        // A player/hopper can unpack the original story table before a complete
                        // structure is ready. The native fill hook leaves its receipt in that chest.
                        if (!nbt.contains("LootTable") && STORY_TABLE.toString().equals(
                                nbt.getCompound("NeoForgeData").getString(CampStoryBooks.TABLE_RECEIPT))) {
                            table = STORY_TABLE;
                        }
                        if (table == null || !table.getNamespace().equals("callresponse")
                                || !Set.of("chests/betrayal_outpost_kitchen", "chests/betrayal_outpost_storage",
                                "chests/betrayal_outpost_enchanted", "chests/betrayal_outpost_valuables",
                                "chests/betrayal_outpost_story").contains(table.getPath())) continue;
                        BlockPos other = null;
                        var state = entity.getBlockState();
                        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                            other = pos.relative(ChestBlock.getConnectedDirection(state));
                            BlockEntity companion = level.getBlockEntity(other);
                            // New collaborator templates can intentionally give each half its own roll.
                            // Only alias an empty half; never make two loot-bearing halves alias each other.
                            if (companion != null && companion.saveWithoutMetadata(level.registryAccess()).contains("LootTable")) other = null;
                        }
                        containers.add(new ContainerInfo(pos.immutable(), other, table, nbt.getLong("LootTableSeed")));
                    }
                }
            }
        }
        // The same technical block can belong to an explicitly different camp type.
        markers.removeIf(OutpostMarkerBlockEntity::isWanderingCamp);
        var anchors = markers.stream().filter(OutpostMarkerBlockEntity::isAnchor).toList();
        if (anchors.size() != 1) {
            CallResponseMod.LOGGER.warn("Outpost {} has {} anchors; leaving legacy/unmarked structure untouched", box, anchors.size());
            return null;
        }
        var spawns = markers.stream().filter(marker -> !marker.isAnchor())
                .sorted(Comparator.comparingLong(marker -> marker.getBlockPos().asLong())).toList();
        return new CampLayout(anchors.get(0).getBlockPos().immutable(),
                spawns.stream().map(marker -> marker.getBlockPos().immutable()).toList(), List.copyOf(containers),
                spawns.stream().map(OutpostMarkerBlockEntity::spawnRole).toList(),
                markers.stream().map(marker -> marker.getBlockPos().immutable()).toList(), true);
    }

    /** Snapshot before markers are removed; failed slots resume without re-drawing GLY or roles. */
    private static CompoundTag encodeMarkerLayout(CampLayout layout) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Center", layout.center().asLong());
        ListTag spawns = new ListTag();
        for (int i = 0; i < layout.spawnCandidates().size(); i++) {
            CompoundTag point = new CompoundTag();
            point.putLong("Pos", layout.spawnCandidates().get(i).asLong());
            point.putString("Role", layout.roles().get(i).name());
            spawns.add(point);
        }
        tag.put("Spawns", spawns);
        ListTag containers = new ListTag();
        for (ContainerInfo container : layout.containers()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", container.pos().asLong());
            if (container.otherHalf() != null) entry.putLong("Other", container.otherHalf().asLong());
            entry.putString("Table", container.table().toString());
            entry.putLong("Seed", container.seed());
            containers.add(entry);
        }
        tag.put("Containers", containers);
        tag.putLongArray("Markers", layout.markers().stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    private static CampLayout decodeMarkerLayout(CompoundTag tag) {
        List<BlockPos> spawns = new ArrayList<>();
        List<OutpostMarkerRole> roles = new ArrayList<>();
        for (Tag value : tag.getList("Spawns", Tag.TAG_COMPOUND)) {
            CompoundTag point = (CompoundTag) value;
            spawns.add(BlockPos.of(point.getLong("Pos")));
            roles.add(OutpostMarkerRole.valueOf(point.getString("Role")));
        }
        List<ContainerInfo> containers = new ArrayList<>();
        for (Tag value : tag.getList("Containers", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            containers.add(new ContainerInfo(BlockPos.of(entry.getLong("Pos")),
                    entry.contains("Other") ? BlockPos.of(entry.getLong("Other")) : null,
                    ResourceLocation.parse(entry.getString("Table")), entry.getLong("Seed")));
        }
        return new CampLayout(BlockPos.of(tag.getLong("Center")), spawns, containers, roles,
                java.util.Arrays.stream(tag.getLongArray("Markers")).mapToObj(BlockPos::of).toList(), true);
    }


    private static ServerPlayer nearestPlayerWithin(ServerLevel level, BoundingBox box, double range) {
        double centerX = (box.minX() + box.maxX() + 1) * 0.5D;
        double centerY = (box.minY() + box.maxY() + 1) * 0.5D;
        double centerZ = (box.minZ() + box.maxZ() + 1) * 0.5D;
        return level.getServer().getPlayerList().getPlayers().stream()
                // 这里只用玩家 UUID 选择一次流浪名册皮肤；旁观/创造玩家也必须能触发结构初始化。
                .filter(player -> player.serverLevel() == level && player.isAlive()
                        && player.distanceToSqr(centerX, centerY, centerZ) <= range * range)
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(centerX, centerY, centerZ)))
                .orElse(null);
    }


    private record ContainerInfo(BlockPos pos, BlockPos otherHalf, ResourceLocation table, long seed) {}
    private record CampLayout(BlockPos center, List<BlockPos> spawnCandidates, List<ContainerInfo> containers,
                              List<OutpostMarkerRole> roles, List<BlockPos> markers, boolean worldCoordinates) {}

    private static final class PendingOutpost {
        private final String key;
        private final BoundingBox box;
        private final BlockPos origin;
        private final Rotation rotation;
        private long nextAttempt;
        private CampLayout layout;
        private int failures;
        private boolean forceGly;
        private boolean natural;

        private String key() { return key; }
        private BoundingBox box() { return box; }
        private PendingOutpost(String key, BoundingBox box) { this(key, box, BlockPos.ZERO, Rotation.NONE); }
        private PendingOutpost(String key, BoundingBox box, BlockPos origin, Rotation rotation) {
            this.key = key;
            this.box = box;
            this.origin = origin;
            this.rotation = rotation;
        }
    }
    private record InitializationPlan(Map<BlockPos, ResourceKey<LootTable>> containerLoot,
                                      List<MarkerSpawn> spawns, List<BlockPos> markers,
                                      boolean validSpawnMarkers, BlockPos technicalAnchor) {
    }
    public static void initializeMaid(com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity maid,
            String group, BetrayalOutpostMaidData.Role role, BlockPos home) {
        if (maid.isInitialized()) return;
        ServerLevel level = (ServerLevel) maid.level();
        maid.setTame(false, false);
        maid.setOwnerUUID(null);
        maid.setPersistenceRequired();
        BetrayalOutpostMaidData.initialize(maid, group, role, home);
        // Use the fixed vanilla level, not an add-on's expanded maximum.
        var favorability = maid.getFavorabilityManager();
        favorability.add(favorability.getPointByLevel(3) - maid.getFavorability());
        maid.setTame(false, false);
        maid.setOwnerUUID(null);
        maid.setHealth(maid.getMaxHealth());
        BetrayalOutpostMaidData.ensureCampSchedule(maid);
        equipForRole(maid, role, level.getRandom(), level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT));
        maid.getMaidInv().setStackInSlot(0, new ItemStack(Items.GOLDEN_APPLE, 5 + level.getRandom().nextInt(6)));
        maid.getMaidInv().setStackInSlot(1, new ItemStack(Items.BAKED_POTATO, 20));
        setTaskForRole(maid, role);
        if (role != BetrayalOutpostMaidData.Role.GLY) EmotionBetrayalManager.initializeOutpostBetrayer(maid);
        else maid.setAggressive(false);
        maid.markInitialized();
        maid.refreshBrain(level);
    }

    public static void personalizeStandalone(com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity maid) {
        if (!(maid.level() instanceof ServerLevel level)
                || maid.getPersistentData().getBoolean("CallResponseOutpostPersonalized")) return;
        ServerPlayer reference = level.players().stream().min(Comparator.comparingDouble(maid::distanceToSqr)).orElse(null);
        if (reference == null) return;
        maid.setModelId(WanderingMaidManager.selectSharedModel(level, reference.getUUID()));
        if (!BetrayalOutpostMaidData.isGly(maid)) applyRevengeHealth(maid,
                Math.max(0, reference.getStats().getValue(Stats.ENTITY_KILLED.get(EntityMaid.TYPE))));
        maid.setHealth(maid.getMaxHealth());
        maid.getPersistentData().putBoolean("CallResponseOutpostPersonalized", true);
    }
    @SubscribeEvent
    public void onOutpostCombatHurt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0) recordCampCombat(event.getEntity(), event.getSource());
        if (event.getEntity() instanceof EntityMaid surrendered
                && com.github.JumDa5he.callresponse.compat.winefox.WineFoxSurrender.isSurrendered(surrendered)) return;
        if (event.getNewDamage() > 0 && event.getEntity() instanceof EntityMaid maid
                && BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            Entity source = event.getSource().getEntity();
            BetrayalOutpostAlertManager.onHurt(maid, source instanceof LivingEntity living ? living : null);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onNaturalCampAttack(LivingIncomingDamageEvent event) {
        if (event.getAmount() > 0) recordCampCombat(event.getEntity(), event.getSource());
    }

    /** A real attributed attack is sufficient, including a hit blocked by a player's shield. */
    public static void recordCampCombat(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
        if (!(victim.level() instanceof ServerLevel)) return;
        Entity actor = attackActor(source);
        if (victim instanceof EntityMaid guard) registerCampParticipant(guard, actor);
        if (actor instanceof EntityMaid guard && victim instanceof ServerPlayer player)
            registerCampParticipant(guard, player);
    }

    private static Entity attackActor(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) return null;
        Entity actor = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        Set<UUID> visited = new java.util.HashSet<>();
        for (int depth = 0; actor instanceof net.minecraft.world.entity.projectile.Projectile projectile
                && depth < 8 && visited.add(actor.getUUID()); depth++) {
            Entity owner = projectile.getOwner();
            if (owner == null) return null;
            actor = owner;
        }
        return actor;
    }

    private static void registerCampParticipant(EntityMaid guard, Entity actor) {
        if (!(guard.level() instanceof ServerLevel level) || actor == null || actor.level() != level
                || !BetrayalOutpostMaidData.isOutpostMaid(guard) || BetrayalOutpostMaidData.isGly(guard)
                || com.github.JumDa5he.callresponse.compat.winefox.WineFoxSurrender.isSurrendered(guard)) return;
        UUID owner = null;
        if (actor instanceof ServerPlayer player) {
            if (player.isSpectator()) return;
            owner = player.getUUID();
        } else if (actor instanceof net.minecraft.world.entity.OwnableEntity owned) {
            owner = owned.getOwnerUUID(); // Standard owner UUID remains useful while the player is offline.
        }
        if (owner == null) return;
        BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
        String group = BetrayalOutpostMaidData.group(guard);
        var camp = data.findByKey(level, group);
        if (camp == null) return;
        double radius = BetrayalOutpostMaidData.CAMP_ACTIVITY_RADIUS;
        if (actor.distanceToSqr(camp.center().getX() + 0.5D, camp.center().getY(), camp.center().getZ() + 0.5D)
                <= radius * radius) data.recordParticipant(group, guard.getUUID(), owner);
    }

    /** Invoked only from the existing EntityMaid death-confirmed paths, never LivingDeathEvent. */
    public static void onConfirmedGuardDeath(EntityMaid maid, net.minecraft.world.damagesource.DamageSource source) {
        if (!(maid.level() instanceof ServerLevel level) || !BetrayalOutpostMaidData.isOutpostMaid(maid)
                || BetrayalOutpostMaidData.isGly(maid)) return;
        registerCampParticipant(maid, attackActor(source));
        BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
        String group = BetrayalOutpostMaidData.group(maid);
        if (data.confirmGuardDeath(group, maid.getUUID())) settleNaturalCamp(level, data, group);
    }

    public static boolean naturalRewardsAllowed(long overworldGameTime, int protectionDays) {
        return Math.max(0L, overworldGameTime) / 24000L >= Math.max(0, Math.min(100, protectionDays));
    }

    private static void settleNaturalCamp(ServerLevel level, BetrayalOutpostSavedData data, String group) {
        var plan = data.plan(group);
        if (plan == null || !plan.natural() || plan.rewardSettled()) return;
        var camp = data.findByKey(level, group);
        List<ServerPlayer> inside = camp == null ? List.of()
                : level.getPlayers(player -> camp.box().isInside(player.blockPosition()));
        Set<UUID> insideIds = playerIds(inside);
        if (!data.settleClearedCamp(group, naturalRewardsAllowed(level), insideIds)) return;
        inside.forEach(BetrayalOutpostManager::deliverClearRewards);
        for (UUID participant : plan.participants()) {
            if (insideIds.contains(participant)) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(participant);
            if (player != null) deliverClearRewards(player);
        }
    }

    /** Recheck occupied natural camps even when a guard went missing without a death event. */
    static void checkNaturalCampArea(ServerLevel level) {
        if (level.players().isEmpty()) return;
        var data = BetrayalOutpostSavedData.get(level);
        Map<BetrayalOutpostSavedData.Outpost, List<ServerPlayer>> occupied = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            for (var camp : data.naturalCampsAt(level, player.blockPosition())) {
                occupied.computeIfAbsent(camp, ignored -> new ArrayList<>()).add(player);
            }
        }
        for (var entry : occupied.entrySet()) {
            var camp = entry.getKey();
            BoundingBox box = camp.box();
            if (!campEntitiesLoaded(level, box)) continue;
            var guards = level.getEntitiesOfClass(EntityMaid.class, AABB.of(box), maid -> maid.isAlive()
                    && box.isInside(maid.blockPosition()) && camp.key().equals(BetrayalOutpostMaidData.group(maid))
                    && !BetrayalOutpostMaidData.isGly(maid));
            if (!guards.isEmpty()) continue;
            List<ServerPlayer> inside = entry.getValue();
            if (data.settleEmptyCamp(camp.key(), naturalRewardsAllowed(level), playerIds(inside))) {
                inside.forEach(BetrayalOutpostManager::deliverClearRewards);
            }
        }
    }

    private static boolean campEntitiesLoaded(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null || !chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)
                        || !level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
            }
        }
        return true;
    }

    private static Set<UUID> playerIds(List<ServerPlayer> players) {
        Set<UUID> ids = new HashSet<>();
        for (ServerPlayer player : players) ids.add(player.getUUID());
        return ids;
    }

    private static boolean naturalRewardsAllowed(ServerLevel level) {
        return naturalRewardsAllowed(level.getServer().overworld().getGameTime(),
                com.github.JumDa5he.callresponse.config.EmotionPassiveConfig.NATURAL_PURSUIT_PROTECTION_DAYS.get());
    }

    @SubscribeEvent
    public void onClearRewardLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) deliverClearRewards(player);
    }

    /** Serialized on the server; failed application remains pending and retries at most once per second. */
    public static void deliverClearRewards(ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved() || !DELIVERING_CLEAR_REWARDS.add(player.getUUID())) return;
        try {
            BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(player.serverLevel());
            int remainingBudget = 8;
            for (UUID camp : data.pendingClearRewards(player.getUUID())) {
                if (remainingBudget-- <= 0) break;
                if (!com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions
                        .applyMaliceReward(player, NATURAL_CLEAR_REWARD_TICKS)) break;
                data.consumeClearReward(player.getUUID(), camp);
            }
        } finally {
            DELIVERING_CLEAR_REWARDS.remove(player.getUUID());
        }
    }

}
