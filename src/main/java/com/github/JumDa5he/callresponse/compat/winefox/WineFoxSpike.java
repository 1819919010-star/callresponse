package com.github.JumDa5he.callresponse.compat.winefox;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/** A finite falling column. Its landing position is fixed when cast, never homing. */
public final class WineFoxSpike extends Entity {
    public static final float WIDTH = 0.22F;
    public static final float HEIGHT = 1.6F;
    public static final int LIFETIME = 40;
    // Accelerate before each move: ordinary three-block headroom is crossed in two ticks.
    private static final double INITIAL_FALL_SPEED = 1.20, FALL_ACCELERATION = 0.30, MAX_FALL_SPEED = 2.10;
    private UUID ownerId;
    private UUID targetId;
    private float damage;
    private boolean spent;

    public WineFoxSpike(EntityType<? extends WineFoxSpike> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setDeltaMovement(0, -INITIAL_FALL_SPEED, 0);
    }

    /** Returns false without spawning if no visible, loaded, unobstructed headroom exists. */
    public static boolean spawn(WineFoxEntity owner, LivingEntity target, float damage) {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive() || target.level() != level
                || !owner.validTarget(target) || !owner.visibleFrom(owner.getEyePosition(), target)
                || owner.distanceToSqr(target) <= WineFoxEntity.MELEE_RANGE * WineFoxEntity.MELEE_RANGE
                || owner.distanceToSqr(target) > WineFoxEntity.SHOT_RANGE * WineFoxEntity.SHOT_RANGE) return false;
        Vec3 head = new Vec3(target.getX(), target.getBoundingBox().maxY - 0.05, target.getZ());
        for (double rise = 3; rise >= 1.5; rise -= 0.5) {
            Vec3 origin = head.add(0, rise, 0);
            // The whole column and its route must fit; do not spawn above a roof or inside it.
            AABB route = box(origin).minmax(new AABB(head.x - WIDTH / 2, head.y + 0.06,
                    head.z - WIDTH / 2, head.x + WIDTH / 2, origin.y, head.z + WIDTH / 2));
            if (!loaded(level, route) || !level.getWorldBorder().isWithinBounds(route)
                    || level.getBlockCollisions(null, route).iterator().hasNext()
                    || !clearRay(level, origin, head, owner)) {
                continue;
            }
            WineFoxSpike spike = new WineFoxSpike(WineFoxEntities.SPIKE.get(), level);
            spike.ownerId = owner.getUUID();
            spike.targetId = target.getUUID();
            spike.damage = damage;
            spike.setPos(origin);
            return level.addFreshEntity(spike);
        }
        return false;
    }

    private static boolean loaded(Level level, AABB area) {
        return area.minY >= level.getMinBuildHeight() && area.maxY < level.getMaxBuildHeight()
                && level.hasChunksAt(BlockPos.containing(area.minX, area.minY, area.minZ),
                BlockPos.containing(area.maxX, area.maxY, area.maxZ));
    }

    private static AABB box(Vec3 base) {
        return new AABB(base.x - WIDTH / 2, base.y, base.z - WIDTH / 2,
                base.x + WIDTH / 2, base.y + HEIGHT, base.z + WIDTH / 2);
    }

    private static boolean clearRay(Level level, Vec3 from, Vec3 to, Entity context) {
        return level.hasChunksAt(BlockPos.containing(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z)),
                BlockPos.containing(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z)))
                && level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, context)).getType() == HitResult.Type.MISS;
    }

    @Override
    public void tick() {
        super.tick();
        if (spent || tickCount > LIFETIME) {
            discard();
            return;
        }
        Vec3 from = position();
        Vec3 velocity = new Vec3(0, Math.max(-MAX_FALL_SPEED, getDeltaMovement().y - FALL_ACCELERATION), 0);
        setDeltaMovement(velocity);
        Vec3 to = from.add(velocity);
        if (level().isClientSide) {
            setPos(to);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Entity source = ownerId == null ? null : level.getEntity(ownerId);
        Entity intended = targetId == null ? null : level.getEntity(targetId);
        if (!(source instanceof WineFoxEntity owner) || !owner.isAlive()
                || !(intended instanceof LivingEntity target) || !owner.validTarget(target)) {
            discard();
            return;
        }
        AABB sweep = box(from).minmax(box(to)).inflate(0.001);
        if (!loaded(level, sweep) || !level.getWorldBorder().isWithinBounds(sweep)) {
            discard();
            return;
        }
        double blockFraction = Double.POSITIVE_INFINITY;
        for (VoxelShape shape : level.getBlockCollisions(this, sweep)) {
            for (AABB obstacle : shape.toAabbs()) {
                blockFraction = Math.min(blockFraction, collisionFraction(obstacle, from, to));
            }
        }
        double targetFraction = collisionFraction(target.getBoundingBox(), from, to);
        if (targetFraction < blockFraction && targetFraction <= 1) {
            setPos(from.lerp(to, targetFraction));
            spent = true;
            // A dodged column cannot damage anyone else. Walls at impact still protect its target.
            if (owner.validTarget(target) && owner.visibleFrom(owner.getEyePosition(), target)
                    && clearRay(level, position(), target.getBoundingBox().getCenter(), this)) {
                target.hurt(damageSources().mobProjectile(this, owner), damage);
            }
            discard();
        } else if (blockFraction <= 1) {
            setPos(from.lerp(to, blockFraction));
            discard();
        } else {
            setPos(to);
        }
    }

    /** Swept full column versus a box, including its radius and height, not just a center ray. */
    private static double collisionFraction(AABB obstacle, Vec3 from, Vec3 to) {
        AABB expanded = new AABB(obstacle.minX - WIDTH / 2, obstacle.minY - HEIGHT,
                obstacle.minZ - WIDTH / 2, obstacle.maxX + WIDTH / 2, obstacle.maxY,
                obstacle.maxZ + WIDTH / 2);
        if (expanded.contains(from)) return 0;
        return expanded.clip(from, to).map(hit -> from.distanceTo(hit) / from.distanceTo(to))
                .orElse(Double.POSITIVE_INFINITY);
    }

    @Override protected void defineSynchedData() {}

    // Temporary attacks never resume after a save/load; no owner or target is persisted.
    @Override protected void readAdditionalSaveData(CompoundTag tag) { spent = true; }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
