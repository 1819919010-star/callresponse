package com.github.JumDa5he.callresponse.compat.winefox;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.UUID;

/** Loaded-tick timer for one fox's current standard passenger relation; not the iron cage path. */
public final class WineFoxVehicleEscape {
    public static final int ESCAPE_TICKS = 60;
    private static final int RETRY_TICKS = 20, HORIZONTAL_RADIUS = 6;
    private static final int[] HEIGHTS = {0, 1, -1, 2, -2, 3, -3, 4, -4};
    private static final String SAVE_KEY = "WineFoxVehicleEscape";
    @Nullable private UUID vehicleId;
    private int remaining, retry;

    public void tick(WineFoxEntity fox) {
        if (!(fox.level() instanceof ServerLevel level)) return;
        if (!fox.isAlive() || fox.isRemoved()) { reset(); return; }
        Entity vehicle = fox.getVehicle();
        if (vehicle == null) { reset(); return; }
        if (vehicle.isRemoved() || vehicle.level() != level) return;
        if (!vehicle.getUUID().equals(vehicleId)) {
            vehicleId = vehicle.getUUID();
            remaining = ESCAPE_TICKS;
            retry = 0;
        }
        if (remaining > 0 && --remaining > 0) return;
        if (retry > 0) { retry--; return; }
        Vec3 exit = findExit(level, fox, vehicle);
        if (exit == null) { retry = RETRY_TICKS; return; }

        fox.stopRiding(); // Forge may cancel dismount; in that case do not move or destroy anything.
        if (fox.isPassenger()) { retry = RETRY_TICKS; return; }
        reset();
        fox.getNavigation().stop();
        fox.setPos(exit.x, exit.y, exit.z);
        fox.setDeltaMovement(Vec3.ZERO);
        fox.fallDistance = 0;
        if (isVanillaBreakable(vehicle) && !vehicle.isRemoved()) {
            // A moving TNT cart primes instead of dropping in its native destroy method.
            // Stop only this vanilla vehicle, then use its ordinary damage/destruction/drop path.
            vehicle.setDeltaMovement(Vec3.ZERO);
            vehicle.hurt(fox.damageSources().mobAttack(fox), 5.0F);
        }
    }

    /** Also called after a successful removeVehicle, including a same-tick dismount/remount. */
    public void reset() { vehicleId = null; remaining = retry = 0; }

    public void save(CompoundTag tag) {
        if (vehicleId == null) { tag.remove(SAVE_KEY); return; }
        CompoundTag value = new CompoundTag();
        value.putUUID("Vehicle", vehicleId);
        value.putInt("Remaining", remaining);
        value.putInt("Retry", retry);
        tag.put(SAVE_KEY, value);
    }

    public void load(CompoundTag tag) {
        reset();
        CompoundTag value = tag.getCompound(SAVE_KEY);
        if (!value.hasUUID("Vehicle")) return;
        vehicleId = value.getUUID("Vehicle");
        remaining = Mth.clamp(value.getInt("Remaining"), 0, ESCAPE_TICKS);
        retry = Mth.clamp(value.getInt("Retry"), 0, RETRY_TICKS);
        // Entity loading establishes passenger links before normal entity ticks resume.
    }

    static boolean isVanillaBreakable(Entity vehicle) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
        return key != null && "minecraft".equals(key.getNamespace())
                && (vehicle instanceof Boat || vehicle instanceof AbstractMinecart);
    }

    @Nullable private static Vec3 findExit(ServerLevel level, WineFoxEntity fox, Entity vehicle) {
        BlockPos origin = vehicle.blockPosition();
        for (int radius = 0; radius <= HORIZONTAL_RADIUS; radius++)
            for (int dy : HEIGHTS)
                for (int dx = -radius; dx <= radius; dx++)
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        BlockPos feet = origin.offset(dx, dy, dz);
                        Vec3 exit = Vec3.atBottomCenterOf(feet);
                        AABB body = fox.getBoundingBox().move(exit.subtract(fox.position()));
                        BlockPos low = BlockPos.containing(body.minX - .01, body.minY - 1, body.minZ - .01);
                        BlockPos high = BlockPos.containing(body.maxX + .01, body.maxY + .01, body.maxZ + .01);
                        if (body.minY <= level.getMinBuildHeight() || body.maxY >= level.getMaxBuildHeight()
                                || !level.hasChunksAt(low, high) || !level.getWorldBorder().isWithinBounds(body)
                                || body.intersects(vehicle.getBoundingBox())
                                || !level.loadedAndEntityCanStandOn(feet.below(), fox)
                                || !level.noCollision(fox, body)) continue;
                        if (!level.getEntities(fox, body.inflate(.05), other -> !other.isRemoved()
                                && !other.isSpectator() && (other instanceof LivingEntity || other.canBeCollidedWith())).isEmpty()) continue;
                        boolean safe = true;
                        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
                            BlockState block = level.getBlockState(pos);
                            if (!block.getFluidState().isEmpty() || block.is(BlockTags.FIRE)
                                    || block.is(Blocks.MAGMA_BLOCK) || block.is(Blocks.CACTUS)
                                    || block.is(Blocks.SWEET_BERRY_BUSH) || block.is(Blocks.WITHER_ROSE)
                                    || block.is(Blocks.POWDER_SNOW) || block.is(Blocks.POINTED_DRIPSTONE)
                                    || block.is(BlockTags.LEAVES) || CampfireBlock.isLitCampfire(block)) {
                                safe = false; break;
                            }
                        }
                        if (safe) return exit;
                    }
        return null;
    }
}
