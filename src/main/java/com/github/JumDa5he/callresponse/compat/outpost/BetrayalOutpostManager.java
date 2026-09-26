package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.info.ServerCustomPackLoader;
import com.github.tartaricacid.touhoulittlemaid.util.ParseI18n;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.IOException;
import java.util.UUID;

/** 从实际结构实例和发布模板坐标登记战利品，并生成五名复仇女仆。 */
public final class BetrayalOutpostManager {
    public static final ResourceLocation STRUCTURE_ID = new ResourceLocation("callresponse", "betrayal_maid_outpost");
    private static final ResourceLocation LAYOUT_ID = new ResourceLocation("callresponse", "outpost/betrayal_maid_outpost_layout.json");
    private static final int MAX_SPAWN_ATTEMPTS = 3;
    private static final long RETRY_TICKS = 20L;

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
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk)) return;
        ChunkPos pos = event.getChunk().getPos();
        // Only chunks that actually own a StructureStart are queued. Inspect on the server tick,
        // never from a chunk-load callback, and never infer starts from spacing/salt.
        long packed = pos.toLong();
        Set<Long> queued = queuedChunks.computeIfAbsent(level.dimension(), ignored -> ConcurrentHashMap.newKeySet());
        if (queued.add(packed)) {
            pendingChunks.computeIfAbsent(level.dimension(), ignored -> new ConcurrentLinkedQueue<>()).offer(packed);
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        // Bounded work over load events, not a per-tick scan of the world.
        for (int i = 0; i < 8; i++) inspectOneLoadedChunk(level);
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
            if (data.isLegacySkipped(key)) continue;
            if (data.contains(key) && data.plan(key) == null) {
                // Legacy camps are never re-filled or re-spawned by the new path.
                data.registerStructure(key, level, box, (box.minY() + box.maxY()) / 2);
                continue;
            }
            if (data.plan(key) != null && !data.plan(key).needsSpawnWork()) continue;
            PoolElementStructurePiece piece = start.getPieces().stream()
                    .filter(candidate -> candidate instanceof PoolElementStructurePiece)
                    .map(candidate -> (PoolElementStructurePiece) candidate).findFirst().orElse(null);
            if (piece == null) {
                CallResponseMod.LOGGER.error("复仇女仆据点 {} 没有可用的结构模板片段", key);
                continue;
            }
            pendingOutposts.computeIfAbsent(level.dimension(), ignored -> new ConcurrentHashMap<>())
                    .putIfAbsent(key, new PendingOutpost(key, box, piece.getPosition(), piece.getRotation()));
        }
    }

    private void initializeOneReadyOutpost(ServerLevel level) {
        Map<String, PendingOutpost> pending = pendingOutposts.get(level.dimension());
        if (pending == null || pending.isEmpty()) return;
        BetrayalOutpostSavedData savedData = BetrayalOutpostSavedData.get(level);
        PendingOutpost outpost = pending.values().stream()
                .filter(candidate -> candidate.nextAttempt <= level.getGameTime()
                        && allChunksLoaded(level, candidate.box))
                .findFirst().orElse(null);
        if (outpost == null) return;
        outpost.nextAttempt = level.getGameTime() + RETRY_TICKS;
        try {
            if (outpost.layout == null) outpost.layout = readLayout(level);
            if (outpost.layout == null) {
                pending.remove(outpost.key);
                return;
            }
            CampLayout layout = outpost.layout;
            BlockPos home = worldPos(outpost, layout.center());
            if (!savedData.contains(outpost.key) && savedData.plan(outpost.key) == null
                    && hasLegacyMarkers(level, outpost, layout)) {
                savedData.skipLegacy(outpost.key);
                pending.remove(outpost.key);
                CallResponseMod.LOGGER.warn("据点 {} 使用旧的带矿石标记模板；保留原状，不自动补人或补货", outpost.key);
                return;
            }
            if (!savedData.contains(outpost.key)) {
                // Register the real StructureStart and its native loot independently of maid success.
                ServerPlayer firstPlayer = nearestPlayer(level, outpost.box);
                savedData.createPlan(outpost.key, newSpawnPlan(level, outpost.key, layout),
                        firstPlayer == null ? null : firstPlayer.getUUID());
                savedData.markInitialized(outpost.key);
                savedData.registerStructure(outpost.key, level, outpost.box, home);
                CallResponseMod.LOGGER.info("复仇女仆据点已登记：{}，10 个结构出生点与 15 个原生战利品容器", outpost.key);
            }
            // A reload between registration and completion resumes only unfinished slots.
            if (savedData.plan(outpost.key) == null) {
                pending.remove(outpost.key);
                return;
            }
            savedData.markInitialized(outpost.key);
            savedData.registerStructure(outpost.key, level, outpost.box, home);
            registerMissingContainers(level, savedData, outpost, layout);
            spawnMissingMaids(level, savedData, outpost, layout, home);
            personalizeMaids(level, savedData, outpost.key, outpost.box);
            if (!savedData.plan(outpost.key).needsSpawnWork()) pending.remove(outpost.key);
        } catch (RuntimeException exception) {
            CallResponseMod.LOGGER.error("复仇女仆据点局部初始化失败，稍后重试：{}", outpost.key, exception);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) return;
        ServerLevel level = player.serverLevel();
        BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
        BetrayalOutpostSavedData.Outpost camp = data.findAt(level, player.blockPosition());
        if (camp != null && data.plan(camp.key()) != null && data.plan(camp.key()).needsPersonalization()) {
            personalizeMaids(level, data, camp.key(), camp.box());
        }
    }

    private static List<BetrayalOutpostSavedData.SpawnSlot> newSpawnPlan(ServerLevel level, String key, CampLayout layout) {
        List<Integer> shuffled = new ArrayList<>();
        for (int i = 0; i < layout.spawnCandidates().size(); i++) shuffled.add(i);
        Collections.shuffle(shuffled, new java.util.Random(level.getRandom().nextLong()));
        List<BetrayalOutpostMaidData.Role> roles = new ArrayList<>(List.of(
                BetrayalOutpostMaidData.Role.HEAVY,
                BetrayalOutpostMaidData.Role.SWORDSMAN,
                BetrayalOutpostMaidData.Role.SWORDSMAN,
                BetrayalOutpostMaidData.Role.FARMER,
                BetrayalOutpostMaidData.Role.FEEDER));
        Collections.shuffle(roles, new java.util.Random(level.getRandom().nextLong()));
        List<BetrayalOutpostSavedData.SpawnSlot> slots = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.nameUUIDFromBytes((key + "|revenge-maid|" + i).getBytes(StandardCharsets.UTF_8));
            slots.add(new BetrayalOutpostSavedData.SpawnSlot(shuffled.get(i), roles.get(i).name(), id, 0, false, false));
        }
        return slots;
    }

    private static void registerMissingContainers(ServerLevel level, BetrayalOutpostSavedData data,
                                                  PendingOutpost outpost, CampLayout layout) {
        for (ContainerInfo container : layout.containers()) {
            data.registerLootChest(level, outpost.key, worldPos(outpost, container.pos()),
                    container.otherHalf() == null ? null : worldPos(outpost, container.otherHalf()),
                    container.table(), 0L);
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
                if (maid == null || !level.addFreshEntity(maid)) {
                    recordSpawnFailure(data, outpost.key, i, slot, "女仆未能加入世界：" + pos);
                    continue;
                }
                // A slot becomes complete only after the real entity successfully enters the level.
                data.updateSlot(outpost.key, i, new BetrayalOutpostSavedData.SpawnSlot(
                        slot.candidate(), slot.role(), slot.entityId(), slot.attempts(), true, false));
            } catch (RuntimeException exception) {
                recordSpawnFailure(data, outpost.key, i, slot, "实体初始化异常：" + pos);
                CallResponseMod.LOGGER.error("复仇女仆据点 {} 第 {} 个出生位异常", outpost.key, i + 1, exception);
            }
        }
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
        EntityMaid maid = EntityMaid.TYPE.create(level);
        if (maid == null) return null;
        maid.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        maid.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null, null);
        maid.setUUID(slot.entityId());
        maid.setTame(false);
        maid.setOwnerUUID(null);
        maid.setPersistenceRequired();
        BetrayalOutpostMaidData.Role role = BetrayalOutpostMaidData.Role.valueOf(slot.role());
        BetrayalOutpostMaidData.initialize(maid, group, role, home);
        maid.getFavorabilityManager().max();
        maid.setTame(false);
        maid.setOwnerUUID(null);
        maid.setHealth(maid.getMaxHealth());
        BetrayalOutpostMaidData.ensureCampSchedule(maid);
        equipForRole(maid, role, level.getRandom());
        maid.getMaidInv().setStackInSlot(0, new ItemStack(Items.GOLDEN_APPLE, 5 + level.getRandom().nextInt(6)));
        maid.getMaidInv().setStackInSlot(1, new ItemStack(Items.BAKED_POTATO, 20));
        setTaskForRole(maid, role);
        EmotionBetrayalManager.initializeOutpostBetrayer(maid);
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
                    applyRevengeHealth(maid, maidKills);
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
                        new ResourceLocation(container.get("table").getAsString())));
            }
            if (candidates.size() != 10 || containers.size() != 15) {
                throw new IllegalStateException("Unexpected camp layout size");
            }
            return new CampLayout(center, List.copyOf(candidates), List.copyOf(containers));
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
        return outpost.origin.offset(StructureTemplate.transform(local, Mirror.NONE, outpost.rotation, BlockPos.ZERO));
    }

    /** Guard old, already-placed marker camps; the release template itself contains no ore markers. */
    private static boolean hasLegacyMarkers(ServerLevel level, PendingOutpost outpost, CampLayout layout) {
        for (ContainerInfo container : layout.containers()) {
            Block block = level.getBlockState(worldPos(outpost, container.pos()).above()).getBlock();
            if (block == Blocks.GOLD_BLOCK || block == Blocks.DIAMOND_BLOCK
                    || block == Blocks.EMERALD_BLOCK || block == Blocks.NETHERITE_BLOCK
                    || block == Blocks.ANCIENT_DEBRIS) return true;
        }
        return false;
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
        String path = switch (role) {
            case FARMER -> "farm";
            case FEEDER -> "feed";
            default -> "attack";
        };
        TaskManager.findTask(new ResourceLocation("touhou_little_maid", path)).ifPresent(maid::setTask);
    }

    private static void equipForRole(EntityMaid maid, BetrayalOutpostMaidData.Role role, RandomSource random) {
        switch (role) {
            case HEAVY -> {
                maid.setItemSlot(EquipmentSlot.MAINHAND, enchanted(new ItemStack(random.nextFloat() < 0.3F ? Items.DIAMOND_AXE : Items.IRON_AXE), random));
                maid.setItemSlot(EquipmentSlot.OFFHAND, enchanted(new ItemStack(Items.SHIELD), random));
                maid.setItemSlot(EquipmentSlot.HEAD, enchanted(new ItemStack(Items.IRON_HELMET), random));
                maid.setItemSlot(EquipmentSlot.CHEST, enchanted(new ItemStack(Items.DIAMOND_CHESTPLATE), random));
                maid.setItemSlot(EquipmentSlot.LEGS, enchanted(new ItemStack(Items.IRON_LEGGINGS), random));
                maid.setItemSlot(EquipmentSlot.FEET, enchanted(new ItemStack(Items.DIAMOND_BOOTS), random));
                reduceMovementSpeed(maid, 0.7D);
            }
            case SWORDSMAN -> {
                maid.setItemSlot(EquipmentSlot.MAINHAND, enchanted(new ItemStack(random.nextFloat() < 0.25F ? Items.DIAMOND_SWORD : Items.IRON_SWORD), random));
                maid.setItemSlot(EquipmentSlot.HEAD, enchanted(new ItemStack(Items.IRON_HELMET), random));
                maid.setItemSlot(EquipmentSlot.CHEST, enchanted(new ItemStack(Items.IRON_CHESTPLATE), random));
                maid.setItemSlot(EquipmentSlot.LEGS, enchanted(new ItemStack(Items.IRON_LEGGINGS), random));
                maid.setItemSlot(EquipmentSlot.FEET, enchanted(new ItemStack(Items.IRON_BOOTS), random));
                reduceMovementSpeed(maid, 0.9D);
            }
            case FARMER, FEEDER -> {
                maid.setItemSlot(EquipmentSlot.MAINHAND, enchanted(new ItemStack(role == BetrayalOutpostMaidData.Role.FARMER ? Items.IRON_HOE : Items.IRON_SHOVEL), random));
                maid.setItemSlot(EquipmentSlot.HEAD, enchanted(new ItemStack(Items.LEATHER_HELMET), random));
                maid.setItemSlot(EquipmentSlot.CHEST, enchanted(new ItemStack(Items.IRON_CHESTPLATE), random));
                maid.setItemSlot(EquipmentSlot.LEGS, enchanted(new ItemStack(Items.LEATHER_LEGGINGS), random));
                maid.setItemSlot(EquipmentSlot.FEET, enchanted(new ItemStack(Items.IRON_BOOTS), random));
                reduceMovementSpeed(maid, 0.9D);
            }
        }
    }

    private static void reduceMovementSpeed(EntityMaid maid, double multiplier) {
        AttributeInstance movement = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.setBaseValue(movement.getBaseValue() * multiplier);
    }

    private static ItemStack enchanted(ItemStack stack, RandomSource random) {
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem) {
            // 据点的主战武器必定附魔，且锋利至少为 II。
            stack.enchant(Enchantments.SHARPNESS, 2 + random.nextInt(2));
        } else if (random.nextFloat() >= 0.8F) {
            return stack;
        } else if (stack.getItem() instanceof ArmorItem) {
            stack.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 1 + random.nextInt(3));
        } else if (stack.getItem() instanceof HoeItem || stack.getItem() instanceof ShovelItem) {
            stack.enchant(Enchantments.BLOCK_EFFICIENCY, 1 + random.nextInt(3));
        } else {
            stack.enchant(Enchantments.UNBREAKING, 1 + random.nextInt(2));
        }
        return stack;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void coordinateOutpostStacking(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide
                && BetrayalOutpostMaidData.isOutpostMaid(maid)) {
            BetrayalOutpostMaidData.ensureUntamed(maid);
            BetrayalOutpostMaidData.migrateLegacyExtraHealth(maid);
            BetrayalOutpostMaidData.updateLegacyMovementSpeed(maid);
            BetrayalOutpostMaidData.ensureCampSchedule(maid);
            BetrayalOutpostAlertManager.tick(maid);
            EmotionBetrayalManager.tickOutpostCombat(maid);
            BetrayalOutpostStackManager.tick(maid);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void preventSisterDamage(LivingAttackEvent event) {
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

        // Wild TLM maids have no tombstone inventory transfer. Drop only what still remains in real slots.
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
        int line = 1 + level.getRandom().nextInt(10);
        String modelName = ServerCustomPackLoader.SERVER_MAID_MODELS.getInfo(maid.getModelId())
                .map(info -> ParseI18n.parse(info.getName()).getString())
                .orElse(maid.getModelId());
        Component message = Component.translatable("message.callresponse.outpost.death.format",
                modelName, Component.translatable("message.callresponse.outpost.death." + line));
        double rangeSqr = 64.0D * 64.0D;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(maid) <= rangeSqr) player.sendSystemMessage(message);
        }
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

    private static String structureKey(ServerLevel level, BoundingBox box) {
        return level.dimension().location() + "|" + STRUCTURE_ID + "|"
                + box.minX() + "," + box.minY() + "," + box.minZ();
    }

    private record ContainerInfo(BlockPos pos, BlockPos otherHalf, ResourceLocation table) {}
    private record CampLayout(BlockPos center, List<BlockPos> spawnCandidates, List<ContainerInfo> containers) {}

    private static final class PendingOutpost {
        private final String key;
        private final BoundingBox box;
        private final BlockPos origin;
        private final Rotation rotation;
        private long nextAttempt;
        private CampLayout layout;

        private PendingOutpost(String key, BoundingBox box, BlockPos origin, Rotation rotation) {
            this.key = key;
            this.box = box;
            this.origin = origin;
            this.rotation = rotation;
        }
    }
}
