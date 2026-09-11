package com.github.JumDa5he.callresponse.compat.task;

import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** 公主抱运行时状态；不写入女仆 NBT，卸载附属后不会残留控制标记。 */
public final class PrincessCarryManager {
    private static final double SEARCH_RADIUS = 16.0D;
    private static final Map<UUID, CarryRequest> REQUESTS = new HashMap<>();
    private static final Map<UUID, ActiveCarry> ACTIVE = new HashMap<>();

    public static boolean request(ServerPlayer player, EntityMaid maid) {
        if (!validController(player, maid) || !maid.getPassengers().isEmpty()) return false;
        EntityMaid target = findNearestMaid(player.serverLevel(), player, maid);
        if (!validTarget(maid, target)) return false;
        REQUESTS.put(maid.getUUID(), new CarryRequest(player.serverLevel().dimension(), target.getUUID(),
                player.serverLevel().getGameTime() + 600L));
        maid.setInSittingPose(false);
        return true;
    }

    public static LivingEntity getRequestedTarget(ServerLevel level, EntityMaid maid) {
        CarryRequest request = REQUESTS.get(maid.getUUID());
        if (request == null || !request.dimension.equals(level.dimension()) || level.getGameTime() > request.expiresAt) {
            REQUESTS.remove(maid.getUUID());
            return null;
        }
        Entity entity = level.getEntity(request.targetId);
        if (!(entity instanceof LivingEntity living) || !validTarget(maid, living)) {
            REQUESTS.remove(maid.getUUID());
            return null;
        }
        return living;
    }

    public static void finishPickup(EntityMaid maid, LivingEntity target) {
        if (!validTarget(maid, target) || !PrincessCarryTask.isCurrentTask(maid)) {
            cancelRequest(maid);
            return;
        }
        beginCarry(maid, target, true);
        REQUESTS.remove(maid.getUUID());
    }

    /** 给其他正式 Behavior 复用 maid→maid 抱持底层，不要求切换公主抱工作模式。 */
    public static boolean tryDirectMaidPickup(EntityMaid carrier, EntityMaid target) {
        if (!canDirectMaidPickup(carrier, target)) return false;
        return beginCarry(carrier, target, false);
    }

    public static boolean canDirectMaidPickup(EntityMaid carrier, EntityMaid target) {
        return carrier != null && target != null && carrier.isTame()
                && carrier.getOwnerUUID() != null
                && carrier.getOwnerUUID().equals(target.getOwnerUUID())
                && carrier.getPassengers().isEmpty() && validTarget(carrier, target);
    }

    /**
     * 公主抱工作与嫉妒 Behavior 共用的完整 maid→maid 抱持判定。
     * ACTIVE 是服务端权威状态；swingingArms 是 TLM 已同步到客户端的抱持标记，供渲染 Mixin 使用。
     */
    public static boolean isMaidCarrySession(EntityMaid carrier) {
        return carrier != null && carrier.getFirstPassenger() instanceof EntityMaid carried
                && isMaidCarrySession(carrier, carried);
    }

    public static boolean isMaidCarrySession(EntityMaid carrier, EntityMaid carried) {
        if (carrier == null || carried == null || carried.getVehicle() != carrier) return false;
        if (carrier.level().isClientSide) return carrier.isSwingingArms();
        ActiveCarry active = ACTIVE.get(carrier.getUUID());
        return active != null && active.targetId.equals(carried.getUUID());
    }

    private static boolean beginCarry(EntityMaid maid, LivingEntity target, boolean taskBound) {
        if (target instanceof EntityMaid carriedMaid) {
            carriedMaid.setInSittingPose(false);
            carriedMaid.getNavigation().stop();
            carriedMaid.setTarget(null);
            setCarriedMaidPhysics(carriedMaid, true);
        }
        if (target.startRiding(maid, true)) {
            // 靠近目标时留下的 WALK_TARGET 已经指向怀里的乘客，必须只在抱起瞬间清理一次。
            // 后续不再碰 carrier 的导航，由 TLM 正常跟随/工作 Behavior 重新下发目标。
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            maid.getNavigation().stop();
            maid.setSwingingArms(true);
            ACTIVE.put(maid.getUUID(), new ActiveCarry(maid.level().dimension(), target.getUUID(), taskBound));
            return true;
        } else if (target instanceof EntityMaid carriedMaid) {
            setCarriedMaidPhysics(carriedMaid, false);
        }
        return false;
    }

    public static void cancelRequest(EntityMaid maid) {
        REQUESTS.remove(maid.getUUID());
    }

    private static EntityMaid findNearestMaid(ServerLevel level, ServerPlayer owner, EntityMaid carrier) {
        return level.getEntitiesOfClass(EntityMaid.class, carrier.getBoundingBox().inflate(SEARCH_RADIUS),
                        other -> other != carrier && other.isOwnedBy(owner) && validTarget(carrier, other))
                .stream().min(Comparator.comparingDouble(carrier::distanceToSqr)).orElse(null);
    }

    private static boolean validController(ServerPlayer player, EntityMaid maid) {
        return maid.isAlive() && maid.isOwnedBy(player) && player.canReach(maid, 6)
                && PrincessCarryTask.isCurrentTask(maid) && PrincessCarryTask.hasSaddle(maid);
    }

    private static boolean validTarget(EntityMaid carrier, LivingEntity target) {
        if (target == null || target == carrier || !target.isAlive() || target.isRemoved()
                || target.isSleeping() || target.isPassenger() || !target.getPassengers().isEmpty()
                || target.level() != carrier.level()) {
            return false;
        }
        return !(target instanceof EntityMaid maid)
                || !MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, ActiveCarry>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveCarry> entry = iterator.next();
            ServerLevel level = server.getLevel(entry.getValue().dimension);
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (!(entity instanceof EntityMaid carrier)) {
                if (level != null) {
                    Entity target = level.getEntity(entry.getValue().targetId);
                    if (target instanceof LivingEntity living) {
                        safeReleaseCarriedEntity(level, null, living);
                    }
                }
                iterator.remove();
                continue;
            }
            Entity target = level.getEntity(entry.getValue().targetId);
            boolean valid = carrier.isAlive() && !carrier.isRemoved()
                    && (!entry.getValue().taskBound || PrincessCarryTask.isCurrentTask(carrier))
                    && target instanceof LivingEntity living && living.isAlive() && living.getVehicle() == carrier;
            if (!valid) {
                if (target instanceof LivingEntity living) {
                    safeReleaseCarriedEntity(level, carrier, living);
                }
                carrier.setSwingingArms(false);
                iterator.remove();
                continue;
            }
            carrier.setSwingingArms(true);
            if (target instanceof EntityMaid carriedMaid) {
                // 只稳定被抱者；不能像旧实现那样每 tick 停止抱人女仆的导航。
                carriedMaid.getNavigation().stop();
                carriedMaid.setTarget(null);
                carriedMaid.setYBodyRot(carrier.getYRot());
                carriedMaid.setYHeadRot(carrier.getYRot());
                setCarriedMaidPhysics(carriedMaid, true);
            }
        }
    }

    /** TLM 本体已有的 Shift 右键下车也能处理；这里仅确保目标女仆优先安全放下。 */
    @SubscribeEvent
    public void onInteractCarriedMaid(PlayerInteractEvent.EntityInteract event) {
        if (!event.getEntity().isShiftKeyDown() || !(event.getTarget() instanceof EntityMaid target)
                || !(target.getVehicle() instanceof EntityMaid carrier)
                || !carrier.isOwnedBy(event.getEntity()) || !PrincessCarryTask.isCurrentTask(carrier)) {
            return;
        }
        if (!event.getLevel().isClientSide) {
            releaseCarrier(carrier);
        }
        event.setCanceled(true);
    }

    /** 魂符、照片等保存发生在 discard 前，因此必须在该事件里先放下乘客并清掉已写入的乘客 NBT。 */
    @SubscribeEvent
    public void onMaidStored(MaidAndItemTransformEvent.ToItem event) {
        EntityMaid maid = event.getMaid();
        if (ACTIVE.containsKey(maid.getUUID())
                || (PrincessCarryTask.isCurrentTask(maid) && !maid.getPassengers().isEmpty())) {
            releaseCarrier(maid);
            event.getData().remove("Passengers");
        }
        releaseIfCarriedTarget(maid);
    }

    /**
     * 魂符在发出 ToItem 事件前就会调用 saveWithoutId；因此还需要在保存入口前先放下真实乘客。
     * 这个入口只处理公主抱 carrier/target，不干预女仆乘船、坐椅等普通骑乘关系。
     */
    public static void releaseBeforeStorage(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel)) return;
        if (ACTIVE.containsKey(maid.getUUID())
                || (PrincessCarryTask.isCurrentTask(maid) && !maid.getPassengers().isEmpty())) {
            releaseCarrier(maid);
        }
        releaseIfCarriedTarget(maid);
    }

    /** 死亡发生在实体彻底移除前，此时仍能用 carrier 的位置寻找安全落点。 */
    @SubscribeEvent
    public void onCarrierDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) {
            releaseBeforeRemoval(maid);
        }
    }

    /** 覆盖 discard、区块卸载等离开世界的路径，防止 passenger 留在已消失的 carrier 上。 */
    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof EntityMaid carrier
                && (ACTIVE.containsKey(carrier.getUUID())
                || (PrincessCarryTask.isCurrentTask(carrier) && !carrier.getPassengers().isEmpty()))) {
            releaseCarrier(carrier);
            return;
        }
        if (event.getEntity() instanceof LivingEntity living) {
            releaseIfCarriedTarget(living);
        }
    }

    /** remove/discard 的 Mixin 入口；不依赖 ACTIVE，真实乘客关系仍在就必须先安全放下。 */
    public static void releaseBeforeRemoval(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel)) return;
        if (ACTIVE.containsKey(maid.getUUID())
                || (PrincessCarryTask.isCurrentTask(maid) && !maid.getPassengers().isEmpty())) {
            releaseCarrier(maid);
        }
        releaseIfCarriedTarget(maid);
    }

    public static void releaseCarrier(EntityMaid carrier) {
        REQUESTS.remove(carrier.getUUID());
        ActiveCarry active = ACTIVE.remove(carrier.getUUID());
        carrier.setSwingingArms(false);
        if (!(carrier.level() instanceof ServerLevel level)) return;

        Entity target = active == null ? null : level.getEntity(active.targetId);
        if (!(target instanceof LivingEntity)) {
            target = carrier.getPassengers().stream().filter(LivingEntity.class::isInstance)
                    .filter(passenger -> active == null || passenger.getUUID().equals(active.targetId))
                    .findFirst().orElse(null);
        }
        if (target instanceof LivingEntity living) {
            safeReleaseCarriedEntity(level, carrier, living);
        }
    }

    /** 铁笼等系统已经正式接管 passenger 后，只清运输记录，不再执行安全落点传送。 */
    public static void completeExternalTransfer(EntityMaid carrier, LivingEntity target) {
        ActiveCarry active = ACTIVE.get(carrier.getUUID());
        if (active == null || !active.targetId.equals(target.getUUID()) || target.getVehicle() == carrier) {
            return;
        }
        ACTIVE.remove(carrier.getUUID());
        REQUESTS.remove(carrier.getUUID());
        carrier.setSwingingArms(false);
        if (target instanceof EntityMaid maid) {
            setCarriedMaidPhysics(maid, false);
        }
    }

    private static void releaseIfCarriedTarget(LivingEntity target) {
        if (!(target.getVehicle() instanceof EntityMaid carrier)) return;
        ActiveCarry active = ACTIVE.get(carrier.getUUID());
        boolean recordedTarget = active != null && active.targetId.equals(target.getUUID());
        if (!recordedTarget && !PrincessCarryTask.isCurrentTask(carrier)) return;
        ACTIVE.remove(carrier.getUUID());
        carrier.setSwingingArms(false);
        if (target.level() instanceof ServerLevel level) {
            safeReleaseCarriedEntity(level, carrier, target);
        } else {
            target.stopRiding();
            if (target instanceof EntityMaid maid) setCarriedMaidPhysics(maid, false);
        }
    }

    /** 所有正常/异常退出路径共用：先解除关系，再恢复物理状态，最后寻找附近可站立位置。 */
    private static void safeReleaseCarriedEntity(ServerLevel level, EntityMaid carrier, LivingEntity target) {
        Vec3 origin = carrier != null ? carrier.position() : target.position();
        float yaw = carrier != null ? carrier.getYRot() : target.getYRot();
        if (target.isPassenger()) target.stopRiding();
        if (target instanceof EntityMaid maid) {
            setCarriedMaidPhysics(maid, false);
            maid.setInSittingPose(false);
            maid.setPose(Pose.STANDING);
            maid.getNavigation().stop();
            maid.setTarget(null);
        }
        Vec3 safe = findSafeReleasePosition(level, target, origin, yaw);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;
        target.teleportTo(safe.x, safe.y, safe.z);
        target.setYRot(yaw);
        target.setYHeadRot(yaw);
        target.setYBodyRot(yaw);
        target.setXRot(0.0F);
        target.setOnGround(true);
        target.xo = safe.x;
        target.yo = safe.y;
        target.zo = safe.z;
        target.xOld = safe.x;
        target.yOld = safe.y;
        target.zOld = safe.z;
    }

    private static Vec3 findSafeReleasePosition(ServerLevel level, LivingEntity target, Vec3 origin, float yaw) {
        double[][] offsets = {{1.25D, 0.0D}, {1.25D, 0.75D}, {1.25D, -0.75D},
                {0.0D, 1.25D}, {0.0D, -1.25D}, {-1.0D, 0.0D}};
        float radians = yaw * Mth.DEG_TO_RAD;
        for (double[] offset : offsets) {
            double x = origin.x - Mth.sin(radians) * offset[0] + Mth.cos(radians) * offset[1];
            double z = origin.z + Mth.cos(radians) * offset[0] + Mth.sin(radians) * offset[1];
            Vec3 safe = resolveGround(level, target, x, z, origin.y);
            if (safe != null) return safe;
        }
        Vec3 currentGround = resolveGround(level, target, origin.x, origin.z, origin.y);
        return currentGround != null ? currentGround : origin;
    }

    private static Vec3 resolveGround(ServerLevel level, LivingEntity target, double x, double z, double y) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(x, y + 2.0D, z).mutable();
        for (int i = 0; i < 32 && cursor.getY() >= level.getMinBuildHeight(); i++) {
            VoxelShape shape = level.getBlockState(cursor).getCollisionShape(level, cursor);
            if (!shape.isEmpty()) {
                Vec3 candidate = new Vec3(x, cursor.getY() + shape.max(Direction.Axis.Y), z);
                EntityDimensions dimensions = target.getDimensions(Pose.STANDING);
                AABB box = dimensions.makeBoundingBox(candidate.x, candidate.y, candidate.z);
                if (level.noCollision(target, box)) return candidate;
            }
            cursor.move(Direction.DOWN);
        }
        // 极端情况下（高空、洞穴边缘）至少落到该列的安全地表，不能留在虚空继续下坠。
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(x), Mth.floor(z));
        Vec3 surface = new Vec3(x, surfaceY, z);
        EntityDimensions dimensions = target.getDimensions(Pose.STANDING);
        return level.noCollision(target, dimensions.makeBoundingBox(surface.x, surface.y, surface.z))
                ? surface : null;
    }

    private static void setCarriedMaidPhysics(EntityMaid maid, boolean carried) {
        maid.noPhysics = carried;
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0.0F;
        if (carried) {
            maid.getNavigation().stop();
            maid.setTarget(null);
        }
    }

    private record CarryRequest(ResourceKey<Level> dimension, UUID targetId, long expiresAt) {
    }

    private record ActiveCarry(ResourceKey<Level> dimension, UUID targetId, boolean taskBound) {
    }
}
