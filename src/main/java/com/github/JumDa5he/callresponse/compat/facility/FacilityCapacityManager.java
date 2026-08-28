package com.github.JumDa5he.callresponse.compat.facility;

import com.github.JumDa5he.callresponse.compat.facility.FacilityCapacitySavedData.ExtraSeat;
import com.github.JumDa5he.callresponse.compat.facility.FacilityCapacitySavedData.FacilityKey;
import com.github.JumDa5he.callresponse.compat.facility.FacilityCapacitySavedData.FacilityRecord;
import com.github.tartaricacid.touhoulittlemaid.block.BlockJoy;
import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.block.BlockPicnicMat;
import com.github.tartaricacid.touhoulittlemaid.block.properties.PicnicMatPart;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitPoi;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityJoy;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityPicnicMat;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.ArrayList;

/** 统一处理野餐垫、女仆床和 Joy 设施的容量、占用和额外座位。 */
public final class FacilityCapacityManager {
    public static final int PICNIC_DEFAULT_CAPACITY = 4;
    public static final int SINGLE_DEFAULT_CAPACITY = 1;
    public static final int PICNIC_VISUAL_SEAT_COUNT = 20;

    /**
     * 前四项与 TLM 原座完全一致；4~7 是松散内圈；8~15 是每边两个的外圈座；16~19 填补四边中段空隙。
     * 坐标固定而不是每次随机，因此既有野餐的松弛感，也不会在重进或重新计算时抖动。
     */
    private static final Vec3[] PICNIC_VISUAL_SEATS = {
            new Vec3(2, 0, 2), new Vec3(-1, 0, 2), new Vec3(-1, 0, -1), new Vec3(2, 0, -1),
            new Vec3(1.18, 0, 1.34), new Vec3(-0.34, 0, 1.18),
            new Vec3(-0.18, 0, -0.30), new Vec3(1.35, 0, -0.14),
            new Vec3(-0.35, 0, -1.38), new Vec3(1.28, 0, -1.50),
            new Vec3(2.38, 0, -0.25), new Vec3(2.48, 0, 1.35),
            new Vec3(1.35, 0, 2.40), new Vec3(-0.30, 0, 2.52),
            new Vec3(-1.38, 0, 1.28), new Vec3(-1.48, 0, -0.38),
            new Vec3(0.42, 0, -0.72), new Vec3(1.72, 0, 0.38),
            new Vec3(0.58, 0, 1.72), new Vec3(-0.72, 0, 0.62)
    };

    @SubscribeEvent
    public void onRightClickPicnic(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty()
                || event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos center = canonicalize(level, event.getPos());
        if (center == null || !(level.getBlockState(center).getBlock() instanceof BlockPicnicMat)) {
            return;
        }
        if (seatPlayerAtClickedOriginalSeat(level, center, player, event.getHitVec())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BlockPos center = canonicalize(level, event.getPos());
            if (center != null) {
                removeFacility(level, center);
            }
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Set<BlockPos> facilities = new HashSet<>();
        for (BlockPos affected : event.getAffectedBlocks()) {
            BlockPos center = canonicalize(level, affected);
            if (center != null) {
                facilities.add(center);
            }
        }
        facilities.forEach(center -> removeFacility(level, center));
    }

    public static Component getFacilityName(ServerLevel level, BlockPos pos) {
        BlockPos center = canonicalize(level, pos);
        return center == null ? Component.empty() : level.getBlockState(center).getBlock().getName();
    }

    public static boolean isSupported(ServerLevel level, BlockPos pos) {
        BlockPos center = canonicalize(level, pos);
        if (center == null) {
            return false;
        }
        BlockState state = level.getBlockState(center);
        return state.getBlock() instanceof BlockPicnicMat
                || state.getBlock() instanceof BlockMaidBed
                || state.getBlock() instanceof BlockJoy;
    }

    public static int getCapacity(ServerLevel level, BlockPos pos) {
        BlockPos center = canonicalize(level, pos);
        if (center == null) {
            return 0;
        }
        int fallback = defaultCapacity(level.getBlockState(center));
        FacilityRecord record = data(level).get(key(level, center));
        return record == null ? fallback : record.capacity();
    }

    public static int adjustCapacity(ServerLevel level, BlockPos pos, int delta) {
        BlockPos center = canonicalize(level, pos);
        if (center == null || !isSupported(level, center)) {
            return 0;
        }
        int current = getCapacity(level, center);
        int next = Math.max(1, current + delta);
        FacilityRecord record = data(level).getOrCreate(key(level, center), defaultCapacity(level.getBlockState(center)));
        record.setCapacity(next);
        data(level).setDirty();
        return next;
    }

    public static int getCurrentOccupants(ServerLevel level, BlockPos pos) {
        BlockPos center = canonicalize(level, pos);
        if (center == null) {
            return 0;
        }
        BlockState state = level.getBlockState(center);
        if (state.getBlock() instanceof BlockMaidBed) {
            return countBedOccupants(level, center);
        }
        int count = countOriginalSitOccupants(level, center);
        return count + cleanAndCountExtraSeats(level, center);
    }

    public static boolean hasVacancy(ServerLevel level, BlockPos pos) {
        int capacity = getCapacity(level, pos);
        return capacity > 0 && getCurrentOccupants(level, pos) < capacity;
    }

    public static boolean hasCapacityOverride(ServerLevel level, BlockPos pos) {
        BlockPos center = canonicalize(level, pos);
        return center != null && data(level).get(key(level, center)) != null;
    }

    public static void trySeatExtraPicnic(ServerLevel level, BlockPos pos, EntityMaid maid) {
        BlockPos center = canonicalize(level, pos);
        if (center == null || maid.isPassenger() || !allOriginalPicnicSeatsOccupied(level, center) || !hasVacancy(level, center)) {
            return;
        }
        FacilityRecord record = data(level).getOrCreate(key(level, center), PICNIC_DEFAULT_CAPACITY);
        cleanExtraSeats(level, center, record);
        int order = nextFreeOrder(record.seats(), 4);
        int visualIndex = Math.floorMod(order, PICNIC_VISUAL_SEAT_COUNT);
        Direction facing = level.getBlockState(center).getValue(BlockPicnicMat.FACING);
        Vec3 local = rotateSeat(PICNIC_VISUAL_SEATS[visualIndex], facing);
        Vec3 worldPos = Vec3.atLowerCornerWithOffset(center, local.x, local.y + 0.0625, local.z);
        float rotation = facingCenterYaw(worldPos, center);
        createExtraSeat(level, center, maid, worldPos, rotation, Type.ON_HOME_MEAL.getTypeName(), order, record);
    }

    public static void trySeatExtraJoy(ServerLevel level, BlockPos pos, EntityMaid maid) {
        BlockPos center = canonicalize(level, pos);
        if (center == null || maid.isPassenger() || !hasVacancy(level, center)) {
            return;
        }
        if (!(level.getBlockEntity(center) instanceof TileEntityJoy joy)) {
            return;
        }
        Entity original = level.getEntity(joy.getSitId());
        if (!(original instanceof EntitySit originalSit) || !originalSit.isAlive()) {
            return;
        }
        FacilityRecord record = data(level).getOrCreate(key(level, center), SINGLE_DEFAULT_CAPACITY);
        cleanExtraSeats(level, center, record);
        int order = nextFreeOrder(record.seats(), 1);
        createExtraSeat(level, center, maid, originalSit.position(), originalSit.getYRot(),
                originalSit.getJoyType(), order, record);
    }

    public static void tryUseOccupiedBed(ServerLevel level, EntityMaid maid, BlockPos pos) {
        BlockPos center = canonicalize(level, pos);
        if (center == null || maid.isSleeping() || getCapacity(level, center) <= 1 || !hasVacancy(level, center)) {
            return;
        }
        BlockState state = level.getBlockState(center);
        if (state.getBlock() instanceof BlockMaidBed && state.getValue(BedBlock.OCCUPIED)) {
            maid.startSleeping(center);
            maid.setPos(center.getX() + 0.5, center.getY() + 0.8, center.getZ() + 0.5);
        }
    }

    /** 原版或扩展野餐座都统一看向中心篮子。 */
    public static void facePicnicCenter(ServerLevel level, EntityMaid maid, BlockPos pos) {
        BlockPos center = canonicalize(level, pos);
        if (center != null && maid.getVehicle() instanceof EntitySit sit
                && center.equals(sit.getAssociatedBlockPos())) {
            float yaw = facingCenterYaw(sit.position(), center);
            sit.setYRot(yaw);
            maid.setYRot(yaw);
            maid.setYHeadRot(yaw);
            maid.setYBodyRot(yaw);
        }
    }

    /**
     * 附近只要存在被《呼应》调过容量的床，就用容量规则选床；否则返回 null 让本体原逻辑继续。
     */
    public static BedSearchResult findCapacityAwareBed(ServerLevel level, EntityMaid maid) {
        BlockPos searchCenter = maid.getBrainSearchPos();
        int range = (int) maid.getRestrictRadius();
        List<BlockPos> poiBeds = level.getPoiManager()
                .getInRange(type -> type.value().equals(InitPoi.MAID_BED.get()), searchCenter, range,
                        PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos).toList();
        List<BlockPos> overrideBeds = new ArrayList<>();
        for (FacilityKey facilityKey : data(level).keys()) {
            if (!facilityKey.dimension().equals(level.dimension().location())) {
                continue;
            }
            BlockPos pos = BlockPos.of(facilityKey.pos());
            if (pos.distSqr(searchCenter) > (long) range * range || !level.hasChunkAt(pos)) {
                continue;
            }
            if (level.getBlockState(pos).getBlock() instanceof BlockMaidBed) {
                overrideBeds.add(pos);
            }
        }
        boolean hasOverride = !overrideBeds.isEmpty();
        if (!hasOverride) {
            return new BedSearchResult(false, null);
        }
        Set<BlockPos> beds = new HashSet<>(poiBeds);
        beds.addAll(overrideBeds);
        BlockPos selected = beds.stream()
                .filter(maid::isWithinRestriction)
                .filter(pos -> {
                    BlockPos center = canonicalize(level, pos);
                    if (center == null) {
                        return false;
                    }
                    if (hasCapacityOverride(level, center)) {
                        return hasVacancy(level, center);
                    }
                    return !level.getBlockState(center).getValue(BedBlock.OCCUPIED);
                })
                .min(Comparator.comparingDouble(pos -> pos.distSqr(maid.blockPosition())))
                .orElse(null);
        return new BedSearchResult(true, selected);
    }

    private static boolean seatPlayerAtClickedOriginalSeat(ServerLevel level, BlockPos center, ServerPlayer player,
                                                            BlockHitResult hit) {
        if (player.isPassenger()) {
            return false;
        }
        BlockState state = level.getBlockState(center);
        Direction facing = state.getValue(BlockPicnicMat.FACING);
        int slot = findClickedOriginalSlot(center, hit.getLocation(), facing);
        TileEntityPicnicMat picnic = (TileEntityPicnicMat) level.getBlockEntity(center);
        UUID oldId = picnic.getSitIds()[slot];
        Entity old = oldId.equals(Util.NIL_UUID) ? null : level.getEntity(oldId);
        if (old instanceof EntitySit sit && sit.isAlive()) {
            Entity passenger = sit.getFirstPassenger();
            if (passenger instanceof Player) {
                player.displayClientMessage(Component.translatable("message.callresponse.picnic_seat_occupied"), true);
                return true;
            }
            if (passenger instanceof EntityMaid maid) {
                maid.stopRiding();
            } else if (passenger != null) {
                player.displayClientMessage(Component.translatable("message.callresponse.picnic_seat_occupied"), true);
                return true;
            }
            sit.discard();
        }
        picnic.setSitId(slot, Util.NIL_UUID);
        Vec3 local = PICNIC_VISUAL_SEATS[slot];
        EntitySit sit = new EntitySit(level,
                Vec3.atLowerCornerWithOffset(center, local.x, local.y + 0.0625, local.z),
                Type.ON_HOME_MEAL.getTypeName(), center);
        double y = local.z < 0 ? -1 : 1;
        double x = local.x < 0 ? -1 : 1;
        sit.setYRot((float) Math.toDegrees(Math.atan2(y, x)) + 90);
        level.addFreshEntity(sit);
        picnic.setSitId(slot, sit.getUUID());
        player.startRiding(sit);
        return true;
    }

    private static int findClickedOriginalSlot(BlockPos center, Vec3 hit, Direction facing) {
        int bestSlot = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int semantic = 0; semantic < 4; semantic++) {
            Vec3 rotated = rotateSeat(PICNIC_VISUAL_SEATS[semantic], facing);
            int physicalSlot = originalSlotAt(rotated);
            Vec3 world = Vec3.atLowerCornerWithOffset(center, rotated.x, 0, rotated.z);
            double distance = world.distanceToSqr(hit.x, world.y, hit.z);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestSlot = physicalSlot;
            }
        }
        return bestSlot;
    }

    private static int originalSlotAt(Vec3 pos) {
        for (int i = 0; i < 4; i++) {
            if (PICNIC_VISUAL_SEATS[i].distanceToSqr(pos) < 0.01) {
                return i;
            }
        }
        return 0;
    }

    private static Vec3 rotateSeat(Vec3 seat, Direction facing) {
        double x = seat.x - 0.5;
        double z = seat.z - 0.5;
        return switch (facing) {
            case EAST -> new Vec3(0.5 - z, seat.y, 0.5 + x);
            case SOUTH -> new Vec3(0.5 - x, seat.y, 0.5 - z);
            case WEST -> new Vec3(0.5 + z, seat.y, 0.5 - x);
            default -> seat;
        };
    }

    private static void createExtraSeat(ServerLevel level, BlockPos center, EntityMaid maid, Vec3 position,
                                        float rotation, String joyType, int order, FacilityRecord record) {
        EntitySit sit = new EntitySit(level, position, joyType, center);
        sit.setYRot(rotation);
        level.addFreshEntity(sit);
        if (maid.startRiding(sit)) {
            record.seats().add(new ExtraSeat(maid.getUUID(), sit.getUUID(), order));
            data(level).setDirty();
        } else {
            sit.discard();
        }
    }

    private static float facingCenterYaw(Vec3 position, BlockPos center) {
        double dx = center.getX() + 0.5 - position.x;
        double dz = center.getZ() + 0.5 - position.z;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
    }


    private static int countOriginalSitOccupants(ServerLevel level, BlockPos center) {
        if (level.getBlockEntity(center) instanceof TileEntityPicnicMat picnic) {
            int count = 0;
            for (UUID id : picnic.getSitIds()) {
                Entity sit = id.equals(Util.NIL_UUID) ? null : level.getEntity(id);
                if (sit != null && sit.isAlive() && !sit.getPassengers().isEmpty()) {
                    count++;
                }
            }
            return count;
        }
        if (level.getBlockEntity(center) instanceof TileEntityJoy joy) {
            Entity sit = level.getEntity(joy.getSitId());
            return sit != null && sit.isAlive() && !sit.getPassengers().isEmpty() ? 1 : 0;
        }
        return 0;
    }

    private static int countBedOccupants(ServerLevel level, BlockPos center) {
        AABB box = new AABB(center).inflate(1.0, 1.5, 1.0);
        return level.getEntitiesOfClass(EntityMaid.class, box, maid ->
                maid.isSleeping() && maid.getSleepingPos().map(center::equals).orElse(false)).size();
    }

    private static int cleanAndCountExtraSeats(ServerLevel level, BlockPos center) {
        FacilityRecord record = data(level).get(key(level, center));
        if (record == null) {
            return 0;
        }
        cleanExtraSeats(level, center, record);
        return record.seats().size();
    }

    private static void cleanExtraSeats(ServerLevel level, BlockPos center, FacilityRecord record) {
        boolean changed = false;
        Iterator<ExtraSeat> iterator = record.seats().iterator();
        while (iterator.hasNext()) {
            ExtraSeat seat = iterator.next();
            Entity entity = level.getEntity(seat.sitId());
            if (!(entity instanceof EntitySit sit) || !sit.isAlive()
                    || !center.equals(sit.getAssociatedBlockPos())
                    || sit.getPassengers().stream().noneMatch(passenger -> passenger.getUUID().equals(seat.userId()))) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            data(level).setDirty();
        }
    }

    private static boolean allOriginalPicnicSeatsOccupied(ServerLevel level, BlockPos center) {
        if (!(level.getBlockEntity(center) instanceof TileEntityPicnicMat picnic)) {
            return false;
        }
        for (UUID id : picnic.getSitIds()) {
            Entity entity = id.equals(Util.NIL_UUID) ? null : level.getEntity(id);
            if (entity == null || !entity.isAlive() || entity.getPassengers().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static int nextFreeOrder(List<ExtraSeat> seats, int start) {
        Set<Integer> used = new HashSet<>();
        for (ExtraSeat seat : seats) {
            used.add(seat.order());
        }
        int order = start;
        while (used.contains(order)) {
            order++;
        }
        return order;
    }

    public static BlockPos canonicalize(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockPicnicMat) {
            if (level.getBlockEntity(pos) instanceof TileEntityPicnicMat picnic) {
                return picnic.getCenterPos();
            }
            return state.getValue(BlockPicnicMat.PART) == PicnicMatPart.CENTER ? pos : null;
        }
        if (state.getBlock() instanceof BlockMaidBed) {
            return state.getValue(BlockMaidBed.PART) == BedPart.HEAD
                    ? pos : pos.relative(state.getValue(BlockMaidBed.FACING));
        }
        if (state.getBlock() instanceof BlockJoy && level.getBlockEntity(pos) instanceof TileEntityJoy) {
            return pos;
        }
        return null;
    }

    private static int defaultCapacity(BlockState state) {
        return state.getBlock() instanceof BlockPicnicMat ? PICNIC_DEFAULT_CAPACITY : SINGLE_DEFAULT_CAPACITY;
    }

    private static FacilityCapacitySavedData data(ServerLevel level) {
        return FacilityCapacitySavedData.get(level.getServer());
    }

    private static FacilityKey key(ServerLevel level, BlockPos center) {
        return new FacilityKey(level.dimension().location(), center.asLong());
    }

    private static void removeFacility(ServerLevel level, BlockPos center) {
        FacilityRecord record = data(level).get(key(level, center));
        if (record != null) {
            for (ExtraSeat seat : record.seats()) {
                Entity entity = level.getEntity(seat.sitId());
                if (entity instanceof EntitySit) {
                    entity.discard();
                }
            }
            data(level).remove(key(level, center));
        }
    }

    public record BedSearchResult(boolean handled, BlockPos pos) {
    }
}
