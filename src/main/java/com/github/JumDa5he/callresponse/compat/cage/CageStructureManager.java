package com.github.JumDa5he.callresponse.compat.cage;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** 在原版哨塔、村庄和要塞首次加载时注入简单铁笼组。 */
public final class CageStructureManager {
    private static final Set<String> PROCESSING = new HashSet<>();
    private static final UUID DEFAULT_POOL_OWNER = new UUID(0L, 0L);
    private final Map<ResourceKey<Level>, Queue<Long>> pendingChunks = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, Set<Long>> queuedChunks = new ConcurrentHashMap<>();

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // ChunkEvent 可能仍处在区块生成/晋升调用栈中。这里严禁读取结构、改方块或生成实体，
        // 否则在整合包中容易发生同步区块加载递归，表现为生物、指令和退出全部冻结。
        long chunkPos = event.getChunk().getPos().toLong();
        Set<Long> queued = queuedChunks.computeIfAbsent(level.dimension(), key -> ConcurrentHashMap.newKeySet());
        if (queued.add(chunkPos)) {
            pendingChunks.computeIfAbsent(level.dimension(), key -> new ConcurrentLinkedQueue<>()).offer(chunkPos);
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Queue<Long> pending = pendingChunks.get(level.dimension());
        if (pending == null) return;
        Long packed = pending.poll();
        if (packed == null) return;
        Set<Long> queued = queuedChunks.get(level.dimension());
        if (queued != null) queued.remove(packed);

        int chunkX = net.minecraft.world.level.ChunkPos.getX(packed);
        int chunkZ = net.minecraft.world.level.ChunkPos.getZ(packed);
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk != null) {
            // 每个维度每 tick 最多审查一个完整 LevelChunk，避免一次加载大量区块时挤占主线程。
            inspectStarts(level, chunk);
        }
    }

    private static void inspectStarts(ServerLevel level, ChunkAccess chunk) {
        // ChunkEvent 回调后任务才会执行；期间区块可能已经卸载，禁止重新把它同步加载回来。
        if (level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) == null) return;
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet()) {
            StructureStart start = entry.getValue();
            if (start == null || !start.isValid()) continue;
            ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                    .getKey(entry.getKey());
            if (id == null || !"minecraft".equals(id.getNamespace())) continue;

            CageOrigin origin;
            int groups;
            if ("pillager_outpost".equals(id.getPath())) {
                origin = CageOrigin.PILLAGER_OUTPOST;
                groups = 1;
            } else if (id.getPath().startsWith("village_")) {
                origin = CageOrigin.VILLAGE;
                groups = 2;
            } else if ("stronghold".equals(id.getPath())) {
                origin = CageOrigin.STRONGHOLD;
                groups = 1;
            } else {
                continue;
            }

            BoundingBox box = start.getBoundingBox();
            String key = level.dimension().location() + "|" + id + "|"
                    + box.minX() + "," + box.minZ();
            CageStructureSavedData data = CageStructureSavedData.get(level);
            if (data.contains(key) || !PROCESSING.add(key)) continue;
            boolean placed = false;
            try {
                placed = placeStructureGroups(level, start, origin, groups);
                if (placed) data.markProcessed(key);
            } catch (RuntimeException exception) {
                CallResponseMod.LOGGER.error("铁笼结构注入失败：{}", key, exception);
            } finally {
                PROCESSING.remove(key);
            }
        }
    }

    private static boolean placeStructureGroups(ServerLevel level, StructureStart start,
                                                CageOrigin origin, int groupCount) {
        if (origin == CageOrigin.STRONGHOLD) {
            return placePortalRoomGroup(level, start);
        }
        List<GroupPlan> plans = new ArrayList<>(groupCount);
        Set<BlockPos> reserved = new HashSet<>();
        for (int group = 0; group < groupCount; group++) {
            int cageCount = 1 + level.getRandom().nextInt(3);
            GroupPlan plan = findGroupNearBuilding(level, start, cageCount, group, reserved);
            // 全部位置都找到后再真正放置，防止第二组失败时第一组被重复生成。
            if (plan == null) return false;
            plans.add(plan);
            reserved.addAll(plan.positions());
        }
        for (GroupPlan plan : plans) {
            for (BlockPos cagePos : plan.positions()) {
                DarkIronCageBlock.placeComplete(level, cagePos, plan.facing(), origin);
                spawnCagedMaid(level, cagePos, origin);
            }
        }
        return true;
    }

    private static GroupPlan findGroupNearBuilding(ServerLevel level, StructureStart start,
                                                   int cageCount, int groupIndex,
                                                   Set<BlockPos> reserved) {
        List<StructurePiece> buildings = start.getPieces().stream()
                // 道路片段通常很扁；优先选择具备实际墙体高度的建筑片段。
                .filter(piece -> piece.getBoundingBox().maxY() - piece.getBoundingBox().minY() >= 3)
                .toList();
        if (buildings.isEmpty()) buildings = start.getPieces();
        if (buildings.isEmpty()) return null;

        int first = level.getRandom().nextInt(buildings.size());
        // 单次区块加载只做有限次只读尝试，不能因为大型村庄在同一 tick 扫描数千个位置。
        int maxAttempts = Math.min(128, Math.max(16, buildings.size() * 8));
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            StructurePiece piece = buildings.get((first + groupIndex * 7 + attempt) % buildings.size());
            BoundingBox box = piece.getBoundingBox();
            Direction outside = Direction.Plane.HORIZONTAL.getRandomDirection(level.getRandom());
            Direction line = outside.getClockWise();
            int distance = 2 + attempt % 3;
            int centerX = (box.minX() + box.maxX()) / 2 + outside.getStepX() * distance;
            int centerZ = (box.minZ() + box.maxZ()) / 2 + outside.getStepZ() * distance;
            int span = (cageCount - 1) * 2;
            int anchorX = centerX - line.getStepX() * (span / 2);
            int anchorZ = centerZ - line.getStepZ() * (span / 2);
            if (!isColumnLoaded(level, anchorX, anchorZ)) continue;
            int y = loadedHeight(level, anchorX, anchorZ);
            if (y == Integer.MIN_VALUE) continue;
            List<BlockPos> positions = new ArrayList<>(cageCount);
            boolean valid = true;
            for (int i = 0; i < cageCount; i++) {
                BlockPos pos = new BlockPos(anchorX + line.getStepX() * i * 2, y,
                        anchorZ + line.getStepZ() * i * 2);
                if (!isColumnLoaded(level, pos.getX(), pos.getZ())
                        || reserved.contains(pos)
                        || loadedHeight(level, pos.getX(), pos.getZ()) != y
                        || !canPlaceCage(level, pos)) {
                    valid = false;
                    break;
                }
                positions.add(pos);
            }
            if (valid) return new GroupPlan(List.copyOf(positions), outside.getOpposite());
        }
        return null;
    }

    /** 要塞只在真正的末地传送门房间内生成，位置沿房间侧墙错开放置。 */
    private static boolean placePortalRoomGroup(ServerLevel level, StructureStart start) {
        StrongholdPieces.PortalRoom portalRoom = start.getPieces().stream()
                .filter(StrongholdPieces.PortalRoom.class::isInstance)
                .map(StrongholdPieces.PortalRoom.class::cast)
                .findFirst().orElse(null);
        if (portalRoom == null) return false;

        int count = 1 + level.getRandom().nextInt(3);
        for (int side = 0; side < 2; side++) {
            int localX = side == 0 ? 1 : 9;
            List<BlockPos> positions = new ArrayList<>(count);
            boolean valid = true;
            for (int i = 0; i < count; i++) {
                BlockPos pos = portalWorldPos(portalRoom, localX, 1, 6 + i * 2);
                if (!canPlaceCage(level, pos)) {
                    valid = false;
                    break;
                }
                positions.add(pos);
            }
            if (!valid) continue;
            Direction facing = portalInwardDirection(portalRoom, side == 0);
            for (BlockPos cagePos : positions) {
                DarkIronCageBlock.placeComplete(level, cagePos, facing, CageOrigin.STRONGHOLD);
                spawnCagedMaid(level, cagePos, CageOrigin.STRONGHOLD);
            }
            return true;
        }
        return false;
    }

    private static BlockPos portalWorldPos(StructurePiece piece, int localX, int localY, int localZ) {
        BoundingBox box = piece.getBoundingBox();
        Direction orientation = piece.getOrientation();
        if (orientation == null) return new BlockPos(localX, localY, localZ);
        int x = switch (orientation) {
            case NORTH, SOUTH -> box.minX() + localX;
            case WEST -> box.maxX() - localZ;
            case EAST -> box.minX() + localZ;
            default -> localX;
        };
        int z = switch (orientation) {
            case NORTH -> box.maxZ() - localZ;
            case SOUTH -> box.minZ() + localZ;
            case WEST, EAST -> box.minZ() + localX;
            default -> localZ;
        };
        return new BlockPos(x, box.minY() + localY, z);
    }

    private static Direction portalInwardDirection(StructurePiece piece, boolean fromLeft) {
        Direction orientation = piece.getOrientation();
        if (orientation == Direction.EAST || orientation == Direction.WEST) {
            return fromLeft ? Direction.SOUTH : Direction.NORTH;
        }
        return fromLeft ? Direction.EAST : Direction.WEST;
    }

    private static boolean canPlaceCage(ServerLevel level, BlockPos pos) {
        if (!isColumnLoaded(level, pos.getX(), pos.getZ())) return false;
        BlockState lower = level.getBlockState(pos);
        BlockState upper = level.getBlockState(pos.above());
        return lower.canBeReplaced() && upper.canBeReplaced()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    private static boolean isColumnLoaded(ServerLevel level, int x, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }

    /** 直接读取已完整加载区块的高度图，绝不通过 ServerLevel#getHeight 同步索取区块。 */
    private static int loadedHeight(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) return Integer.MIN_VALUE;
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    private static void spawnCagedMaid(ServerLevel level, BlockPos cagePos, CageOrigin origin) {
        EntityMaid maid = EntityMaid.TYPE.create(level);
        if (maid == null) return;
        maid.moveTo(cagePos.getX() + 0.5D, cagePos.getY(), cagePos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        maid.finalizeSpawn(level, level.getCurrentDifficultyAt(cagePos), MobSpawnType.STRUCTURE, null);
        ServerPlayer nearest = level.getNearestPlayer(maid, 128.0D) instanceof ServerPlayer player ? player : null;
        UUID poolOwner = nearest == null ? DEFAULT_POOL_OWNER : nearest.getUUID();
        maid.setModelId(WanderingMaidManager.selectSharedModel(level, poolOwner));
        maid.setPersistenceRequired();
        if (!level.addFreshEntity(maid)) return;
        if (level.getBlockEntity(cagePos) instanceof DarkIronCageBlockEntity cage) {
            cage.setOrigin(origin);
            if (cage.capture(maid, nearest, origin)) {
                CageRescueManager.markStructurePrisoner(maid, origin, cagePos);
            }
        }
    }

    private record GroupPlan(List<BlockPos> positions, Direction facing) {
    }
}
