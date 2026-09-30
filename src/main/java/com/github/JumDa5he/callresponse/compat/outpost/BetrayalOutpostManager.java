package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.CallResponseMod;
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
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
    private static final int STRUCTURE_SPACING = 36;
    private static final int STRUCTURE_SEPARATION = 16;
    private static final int STRUCTURE_SALT = 14357625;
    private static final RandomSpreadStructurePlacement STRUCTURE_PLACEMENT =
            new RandomSpreadStructurePlacement(STRUCTURE_SPACING, STRUCTURE_SEPARATION,
                    RandomSpreadType.LINEAR, STRUCTURE_SALT);

    private final Map<ResourceKey<Level>, Queue<Long>> pendingChunks = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, Set<Long>> queuedChunks = new ConcurrentHashMap<>();

    public BetrayalOutpostManager() {
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
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ChunkPos pos = event.getChunk().getPos();
        // 结构使用固定 random_spread 网格；只有每个 36×36 区域中的候选起点才可能持有结构 Start。
        // 在 ChunkEvent 内只做纯数学判断，不读取结构数据，也不触发任何同步区块加载。
        ChunkPos candidate = STRUCTURE_PLACEMENT.getPotentialStructureChunk(level.getSeed(), pos.x, pos.z);
        if (!candidate.equals(pos)) return;
        long packed = pos.toLong();
        Set<Long> queued = queuedChunks.computeIfAbsent(level.dimension(), ignored -> ConcurrentHashMap.newKeySet());
        if (queued.add(packed)) {
            pendingChunks.computeIfAbsent(level.dimension(), ignored -> new ConcurrentLinkedQueue<>()).offer(packed);
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        inspectOneLoadedChunk(level);
        initializeOneReadyOutpost(level);
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

        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            if (start == null || !start.isValid()) continue;
            ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
            if (!STRUCTURE_ID.equals(id)) continue;
            BoundingBox box = start.getBoundingBox();
            String key = structureKey(level, box);
            BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
            if (data.contains(key)) {
                data.registerStructure(key, level, box, (box.minY() + box.maxY()) / 2);
            } else {
                pendingOutposts.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                        .putIfAbsent(key, new PendingOutpost(key, box));
            }
        }
    }

    private void initializeOneReadyOutpost(ServerLevel level) {
        Map<String, PendingOutpost> pending = pendingOutposts.get(level.dimension());
        if (pending == null || pending.isEmpty()) return;
        BetrayalOutpostSavedData savedData = BetrayalOutpostSavedData.get(level);
        pending.entrySet().removeIf(entry -> savedData.contains(entry.getKey()));
        PendingOutpost outpost = pending.values().stream()
                .filter(candidate -> allChunksLoaded(level, candidate.box()))
                .findFirst().orElse(null);
        if (outpost == null) return;
        if (savedData.contains(outpost.key())) {
            pending.remove(outpost.key());
            return;
        }
        try {
            if (!outpost.prepared) {
                // The template already owns its LootTables. Register the placed containers before
                // waiting for a skin-pool player or attempting to create any maids.
                InitializationPlan plan = scanAndValidate(level, outpost);
                int homeY = plan.validSpawnMarkers()
                        ? plan.spawns().stream().mapToInt(spawn -> spawn.pos().getY()).sorted()
                                .skip((MAID_COUNT - 1) / 2).findFirst().orElse(outpost.box().minY())
                        : (outpost.box().minY() + outpost.box().maxY()) / 2;
                savedData.registerStructure(outpost.key(), level, outpost.box(), homeY);
                applyContainerLoot(level, outpost.key(), plan.containerLoot());
                outpost.plan = plan;
                outpost.prepared = true;
                if (!plan.validSpawnMarkers()) {
                    pending.remove(outpost.key());
                    return;
                }
            }
            ServerPlayer skinOwner = nearestPlayer(level, outpost.box());
            InitializationPlan plan = outpost.plan;
            int[] spawnLevels = plan.spawns().stream().mapToInt(spawn -> spawn.pos().getY()).sorted().toArray();
            int homeY = spawnLevels[(spawnLevels.length - 1) / 2];
            BlockPos home = new BlockPos(
                    (outpost.box().minX() + outpost.box().maxX()) / 2,
                    homeY,
                    (outpost.box().minZ() + outpost.box().maxZ()) / 2);
            List<EntityMaid> maids = createMaidGroup(level, plan.spawns(), outpost.key(), home, skinOwner, false);
            if (maids.size() != MAID_COUNT) {
                CallResponseMod.LOGGER.error("复仇女仆据点 {} 无法创建完整的五人小队，本次不初始化", outpost.key());
                return;
            }

            // 先写入世界级持久化标记，再提交方块与实体，避免重启或区块重载重复刷出小队。
            for (EntityMaid maid : maids) {
                if (maid instanceof com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity revenge
                        && plan.technicalAnchor() != null) revenge.rememberCampEntrance(outpost.box(), plan.technicalAnchor());
            }
            savedData.markInitialized(outpost.key());
            savedData.registerStructure(outpost.key(), level, outpost.box(), homeY);
            removeDevelopmentMarkers(level, plan);
            for (EntityMaid maid : maids) {
                if (!level.addFreshEntity(maid)) {
                    CallResponseMod.LOGGER.error("复仇女仆据点 {} 的女仆 {} 生成失败", outpost.key(), maid.getUUID());
                }
            }
            pending.remove(outpost.key());
            CallResponseMod.LOGGER.info("复仇女仆据点已初始化：{}，皮肤池玩家 {}", outpost.key(), (skinOwner == null ? "pending" : skinOwner.getGameProfile().getName()));
        } catch (RuntimeException exception) {
            pending.remove(outpost.key());
            CallResponseMod.LOGGER.error("复仇女仆据点初始化失败：{}", outpost.key(), exception);
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
                data.registerLootChest(level, campKey, entry.getKey(),
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
        ResourceLocation templateId =
                ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "betrayal_maid_outpost_final");
        StructureTemplate template = level.getStructureManager().getOrCreate(templateId);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL)) {
            return false;
        }

        net.minecraft.core.Vec3i size = template.getSize();
        int minX = origin.getX();
        int minY = origin.getY();
        int minZ = origin.getZ();
        int maxX = minX + size.getX() - 1;
        int maxY = minY + size.getY() - 1;
        int maxZ = minZ + size.getZ() - 1;

        BoundingBox box = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
        List<OutpostMarkerBlockEntity> markers = collectMarkers(level, box);

        List<OutpostMarkerBlockEntity> anchors = markers.stream()
                .filter(OutpostMarkerBlockEntity::isAnchor).toList();
        if (anchors.size() != 1) {
            CallResponseMod.LOGGER.error("调试生成失败：需要 1 个 outpost_anchor，实际 {}", anchors.size());
            return false;
        }
        BlockPos home = anchors.get(0).getBlockPos();

        // 调试营地也要写进世界数据，否则 findAt 找不到营地，不祥之兆不会转成营地袭击。
        String campKey = structureKey(level, box);
        BetrayalOutpostSavedData savedData = BetrayalOutpostSavedData.get(level);
        savedData.registerStructure(campKey, level, box, home.getY());

        List<MarkerSpawn> spawns = toSpawns(markers);
        if (spawns.size() < MAID_COUNT) {
            CallResponseMod.LOGGER.error("调试生成失败：出生标记不足 {} 个，实际 {}", MAID_COUNT, spawns.size());
            return false;
        }

        List<EntityMaid> maids = createMaidGroup(level, spawns, campKey, home, player, forceGly);
        if (maids.size() != MAID_COUNT) {
            CallResponseMod.LOGGER.error("调试生成失败：无法按标记凑出 {} 人小队（固定角色标记过多或候选点不足）", MAID_COUNT);
            return false;
        }
        for (EntityMaid maid : maids) {
            if (!level.addFreshEntity(maid)) {
                CallResponseMod.LOGGER.error("调试生成失败：女仆 {} 未能加入世界", maid.getUUID());
                return false;
            }
        }
        savedData.markInitialized(campKey);

        // 调试营地也登记模板自带的表和种子，沿用本地开箱警戒及认可结算。
        applyContainerLoot(level, campKey, scanAndValidate(level, new PendingOutpost(campKey, box)).containerLoot());

        for (OutpostMarkerBlockEntity marker : markers) {
            level.removeBlock(marker.getBlockPos(), false);
        }
        CallResponseMod.LOGGER.info("调试生成复仇女仆据点：{}，campKey={}，forceGly={}", origin, campKey, forceGly);
        return true;
    }

    /** 击杀数直接写入真实 MAX_HEALTH，不再创建第二层伤害血池。 */
    private static void applyRevengeHealth(EntityMaid maid, int maidKills) {
        if (maidKills <= 0) return;
        AttributeInstance maxHealth = maid.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return;

        double limit = BetrayalOutpostMaxHealth.ensureAndGetLimit();
        double bonus = Math.min(maidKills, Math.max(0.0D, limit - maid.getMaxHealth()));
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
                addGlySign(maid);
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

    private static void addGlySign(EntityMaid maid) {
        // 彩蛋牌子只作展示：旁边的女仆看到不会被威慑
        MaidSignManager.attach(maid, Items.OAK_SIGN, false);
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

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof EntityMaid attackingMaid
                && BetrayalOutpostMaidData.areSisters(attackingMaid, victim)) {
            attackingMaid.setTarget(null);
            event.setCanceled(true);
            return;
        }
        if (attacker != null) BetrayalOutpostMaidData.markProvoked(victim);
    }

    /** GLY 只说话不还手；受到有来源的攻击时从 hurt_lines 里说一句。 */
    @SubscribeEvent
    public void onGlyHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide) return;
        if (!BetrayalOutpostMaidData.isOutpostMaid(maid) || !BetrayalOutpostMaidData.isGly(maid)) return;
        if (event.getSource().is(DamageTypes.STARVE)) return;
        if (event.getSource().getEntity() == null && event.getSource().getDirectEntity() == null) return;
        BetrayalOutpostMaidData.tickGlyHurtDialogue(maid);
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && attacker != maid) {
            BetrayalOutpostMaidData.startGlyFlee(maid, attacker);
        }
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
        double centerX = (box.minX() + box.maxX() + 1) * 0.5D;
        double centerY = (box.minY() + box.maxY() + 1) * 0.5D;
        double centerZ = (box.minZ() + box.maxZ() + 1) * 0.5D;
        return level.getServer().getPlayerList().getPlayers().stream()
                // 这里只用玩家 UUID 选择一次流浪名册皮肤；旁观/创造玩家也必须能触发结构初始化。
                .filter(player -> player.serverLevel() == level && player.isAlive())
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(centerX, centerY, centerZ)))
                .orElse(null);
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

    private static final class PendingOutpost {
        private final String key;
        private final BoundingBox box;
        private InitializationPlan plan;
        private boolean prepared;

        private PendingOutpost(String key, BoundingBox box) {
            this.key = key;
            this.box = box;
        }

        private String key() { return key; }
        private BoundingBox box() { return box; }
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
        maid.getFavorabilityManager().max();
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
        if (event.getNewDamage() > 0 && event.getEntity() instanceof EntityMaid maid
                && BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            Entity source = event.getSource().getEntity();
            BetrayalOutpostAlertManager.onHurt(maid, source instanceof LivingEntity living ? living : null);
        }
    }
}
