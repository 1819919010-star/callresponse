package com.github.JumDa5he.callresponse.compat.cage;

import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.brain.JealousyCageManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxSurrender;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxRehabilitation;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;
import java.util.EnumSet;

/**
 * 只保存被关实体的 UUID。实体本身始终留在世界中，照常渲染、受伤和死亡。
 */
public final class DarkIronCageBlockEntity extends BlockEntity {
    private static final int MISSING_ENTITY_GRACE_TICKS = 100;
    /** 恢复为原来的贴近吸附范围，避免隔着一格仍抓不到或误抓远处生物。 */
    private static final double TOUCH_MARGIN = 0.08D;
    private static final double INNER_MIN_OFFSET = 1.0D / 16.0D;
    private static final double INNER_MAX_OFFSET = 15.0D / 16.0D;
    private static final double LOGICAL_MOVE_RADIUS = 0.22D;
    private static final double ROOF_HEIGHT = 2.2D;
    private static final int PRISONER_SPEECH_INTERVAL = 20 * 60;
    private static final int PRISONER_SPEECH_COUNT = 5;
    private static final int BETRAYAL_STRIP_DELAY = 20;
    private static final int BETRAYAL_STRIP_INTERVAL = 10;
    private static final int BETRAYAL_SPEECH_INTERVAL = 20 * 60;
    private static final int BETRAYAL_SPEECH_COUNT = 4;
    private static final long CAGE_EMOTION_COOLDOWN = 20L * 60L * 10L;
    private static final long LIGHTNING_INTERVAL = 20L * 10L;
    private static final int GOLDEN_EFFECT_DURATION = 60;
    private static final String CAGE_EMOTION_UNTIL = "CallResponseCageEmotionUntil";
    private static final String CAGE_WITNESS_UNTIL = "CallResponseCageWitnessUntil";
    private static final String WINE_FOX_CAGE = "CallResponseWineFoxCage";
    private static final int WINE_FOX_ESCAPE_TICKS = 60, WINE_FOX_RETRY_TICKS = 20;
    private enum WineFoxEscapeState { NONE, CAPTURED, ESCAPING, RELEASED }
    private WineFoxEscapeState wineFoxEscapeState = WineFoxEscapeState.NONE;
    private int wineFoxEscapeTicks, wineFoxRetryTicks;
    private long lastWineFoxTick = Long.MIN_VALUE;

    private UUID occupantId;
    private Component occupantName = Component.empty();
    private UUID captorId;
    private UUID placerId;
    private int rehabilitationRetryDelay;
    private Component captorName = Component.empty();
    private CageOrigin origin = CageOrigin.NORMAL;
    private int missingEntityTicks;
    private boolean occupantWasNoGravity;
    private long nextPrisonerSpeechTime;
    private long nextBetrayalStripTime;
    private long nextBetrayalSpeechTime;
    private boolean betrayalStrippingComplete;
    private boolean hasLastSafePosition;
    private double lastSafeX;
    private double lastSafeY;
    private double lastSafeZ;
    private CageEnvironment trackedEnvironment;
    private long nextLightningTime;
    private UUID goldenAppleTargetId;
    private boolean ownsRegeneration;
    private boolean ownsResistance;
    private MobEffectInstance previousRegeneration;
    private MobEffectInstance previousResistance;
    private long regenerationClaimTime;
    private long resistanceClaimTime;

    public DarkIronCageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DARK_IRON_CAGE_ENTITY.get(), pos, state);
        trackedEnvironment = state.getValue(DarkIronCageBlock.ENVIRONMENT);
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  DarkIronCageBlockEntity cage) {
        CageEnvironment environment = state.getValue(DarkIronCageBlock.ENVIRONMENT);
        cage.trackEnvironment(level, environment);
        if (cage.occupantId == null) {
            cage.clearGoldenAppleEffects(cage.goldenAppleTarget(level));
            cage.captureTouchingEntity(level);
            return;
        }
        Entity entity = level.getEntity(cage.occupantId);
        if (entity instanceof WineFoxEntity fox && fox.isDeathConfirmed()) {
            cage.unbindWineFox(fox);
            cage.clearOccupant();
            return;
        }
        // A missing UUID during entity/chunk loading is not a dead or lost boss.
        if (entity == null && (cage.wineFoxEscapeState == WineFoxEscapeState.CAPTURED
                || cage.wineFoxEscapeState == WineFoxEscapeState.ESCAPING)) return;
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
            if (++cage.missingEntityTicks >= MISSING_ENTITY_GRACE_TICKS) {
                cage.clearOccupant();
            }
            return;
        }
        // 玩家切换旁观模式属于管理员脱困手段；不能继续按 UUID 把旁观玩家锁回笼心。
        if (living instanceof Player player && player.isSpectator()) {
            cage.release(null);
            return;
        }
        cage.missingEntityTicks = 0;
        if (living instanceof WineFoxEntity fox && !cage.canClaimWineFox(fox)) {
            cage.clearOccupant(); return; // Do not pull a prisoner out of another valid cage first.
        }
        cage.ensureMaidCageControl(living);
        cage.keepInsideBoundary(living);
        cage.applyEnvironment(level, living, environment);
        cage.tickPrisonerSpeech(level, living);
        cage.tickBetrayalMaid(level, living);
        cage.tickSurrenderedMaid(living);
        if (living instanceof WineFoxEntity fox && fox.isAlive()) cage.tickWineFoxEscape(level, fox);
    }

    public boolean isOccupied() {
        return occupantId != null;
    }

    public @Nullable LivingEntity occupant() {
        if (occupantId == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Entity entity = serverLevel.getEntity(occupantId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    public boolean capture(LivingEntity living, @Nullable Player captor, CageOrigin captureOrigin) {
        if (level == null || level.isClientSide || isOccupied() || !living.isAlive() || living.isSpectator()) {
            return false;
        }
        if (wineFoxEscapeState == WineFoxEscapeState.RELEASED
                || living instanceof WineFoxEntity fox && !canClaimWineFox(fox)) return false;
        living.stopRiding();
        occupantWasNoGravity = living.isNoGravity();
        living.setNoGravity(false);
        clearCrouchingPose(living);
        if (living instanceof Mob mob && mob.isLeashed()) {
            mob.dropLeash(true, false);
        }
        occupantId = living.getUUID();
        occupantName = CageEntityNameHelper.displayName(living);
        captorId = captor == null ? null : captor.getUUID();
        captorName = captor == null ? Component.empty() : captor.getDisplayName();
        origin = captureOrigin == null ? CageOrigin.NORMAL : captureOrigin;
        missingEntityTicks = 0;
        nextPrisonerSpeechTime = level.getGameTime() + PRISONER_SPEECH_INTERVAL
                + living.getRandom().nextInt(101);
        nextBetrayalStripTime = level.getGameTime() + BETRAYAL_STRIP_DELAY;
        nextBetrayalSpeechTime = level.getGameTime() + BETRAYAL_SPEECH_INTERVAL
                + living.getRandom().nextInt(201);
        betrayalStrippingComplete = false;
        if (living instanceof EntityMaid maid && WineFoxSurrender.isSurrendered(maid)) {
            WineFoxSurrender.recordCaptor(maid, captorId != null ? captorId : placerId);
            captorId = WineFoxSurrender.captor(maid);
            CompoundTag surrender = maid.getPersistentData().getCompound(WineFoxSurrender.ROOT);
            if (captor != null && captor.getUUID().equals(captorId) && !surrender.contains("CaptorName"))
                surrender.putString("CaptorName", Component.Serializer.toJson(captor.getDisplayName(), level.registryAccess()));
            captorName = readComponent(surrender, "CaptorName");
        }
        beginMaidCageControl(living);
        placeAtCageCenter(living);
        applyCageEmotion(living, captor);
        if (living instanceof WineFoxEntity fox) {
            bindWineFox(fox);
            wineFoxEscapeState = WineFoxEscapeState.CAPTURED;
            wineFoxEscapeTicks = WINE_FOX_ESCAPE_TICKS;
            wineFoxRetryTicks = 0;
            lastWineFoxTick = level.getGameTime();
        }
        syncOccupied(true);
        setChanged();
        return true;
    }

    public @Nullable LivingEntity release(@Nullable Player leashHolder) {
        LivingEntity living = occupant();
        if (living != null) {
            clearGoldenAppleEffects(living);
            if (living instanceof WineFoxEntity fox) unbindWineFox(fox);
            endMaidCageControl(living);
            Vec3 exit = exitPosition();
            if (living instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.connection.teleport(exit.x, exit.y, exit.z,
                        serverPlayer.getYRot(), serverPlayer.getXRot());
            } else {
                living.setPos(exit.x, exit.y, exit.z);
            }
            living.setDeltaMovement(Vec3.ZERO);
            living.setNoGravity(occupantWasNoGravity);
            clearCrouchingPose(living);
            if (leashHolder != null && living instanceof Mob mob) {
                mob.setLeashedTo(leashHolder, true);
            }
            if (living instanceof EntityMaid maid) {
                CageRescueManager.onReleasedFromCage(maid, worldPosition, origin);
            }
        }
        clearOccupant();
        return living;
    }

    /** 所有玩家环境切换都从这里进入，确保金苹果来源效果能够立即撤销。 */
    public void onEnvironmentChanged(CageEnvironment environment) {
        if (level instanceof ServerLevel serverLevel) {
            if (environment != CageEnvironment.GOLDEN_APPLE) {
                clearGoldenAppleEffects(goldenAppleTarget(serverLevel));
            }
            nextLightningTime = environment == CageEnvironment.LIGHTNING
                    ? serverLevel.getGameTime() + LIGHTNING_INTERVAL : 0L;
        }
        trackedEnvironment = environment;
        setChanged();
    }

    public Component information() {
        LivingEntity current = occupant();
        Component name = current != null ? CageEntityNameHelper.displayName(current)
                : occupantName.getString().isBlank()
                ? Component.translatable("message.callresponse.cage.unknown_entity") : occupantName;
        return switch (origin) {
            case PILLAGER_OUTPOST -> Component.translatable("message.callresponse.cage.info.outpost", name);
            case VILLAGE -> Component.translatable("message.callresponse.cage.info.village", name);
            case STRONGHOLD -> Component.translatable("message.callresponse.cage.info.stronghold", name);
            case NORMAL -> {
                Component captor = captorName.getString().isBlank()
                        ? Component.translatable("message.callresponse.cage.unknown_captor") : captorName;
                yield Component.translatable("message.callresponse.cage.info.normal", name, captor);
            }
        };
    }

    public void setOrigin(CageOrigin value) {
        origin = value == null ? CageOrigin.NORMAL : value;
        setChanged();
    }

    /** Placement ownership is only a fallback when automatic contact captures a surrendered maid. */
    public void setPlacer(Player player) {
        if (level != null && !level.isClientSide && placerId == null) {
            placerId = player.getUUID();
            setChanged();
        }
    }

    private void tickSurrenderedMaid(LivingEntity living) {
        if (!(living instanceof EntityMaid prisoner) || !WineFoxSurrender.isSurrendered(prisoner)
                || !living.getUUID().equals(occupantId)) return;
        if (!WineFoxSurrender.tickInCage(prisoner, captorId != null ? captorId : placerId)) return;
        // A failed creation/addition leaves the real prisoner and completed progress untouched.
        if (rehabilitationRetryDelay > 0) { rehabilitationRetryDelay--; return; }
        rehabilitationRetryDelay = 20;
        WineFoxRehabilitation.convert(prisoner, WineFoxSurrender.captor(prisoner), this);
    }

    /** Switch the cage to the already joined, tamed maid before the source is discarded. */
    public boolean replaceRehabilitatedOccupant(EntityMaid prisoner, EntityMaid replacement) {
        if (isRemoved() || occupant() != prisoner) return false;
        clearGoldenAppleEffects(prisoner);
        endMaidCageControl(prisoner);
        occupantId = replacement.getUUID();
        occupantName = CageEntityNameHelper.displayName(replacement);
        captorId = replacement.getOwnerUUID();
        occupantWasNoGravity = false;
        origin = CageOrigin.NORMAL;
        missingEntityTicks = 0;
        betrayalStrippingComplete = true;
        rehabilitationRetryDelay = 0;
        beginMaidCageControl(replacement);
        placeAtCageCenter(replacement);
        setChanged();
        return true;
    }

    public boolean usesLogicalContainment(@Nullable Entity entity) {
        return entity != null && occupantId != null && occupantId.equals(entity.getUUID())
                && (entity.getBbWidth() > 0.80F || entity.getBbHeight() > 2.10F);
    }

    private void beginMaidCageControl(LivingEntity living) {
        if (!(living instanceof EntityMaid maid)) return;
        MaidMovementControl.begin(maid, MaidMovementControl.Reason.CAGE,
                EnumSet.of(MaidMovementControl.Field.PATH));
        MaidMovementControl.clearNavigation(maid);
    }

    private void ensureMaidCageControl(LivingEntity living) {
        if (living instanceof EntityMaid maid
                && !MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE)) {
            beginMaidCageControl(maid);
        }
    }

    private void endMaidCageControl(LivingEntity living) {
        if (!(living instanceof EntityMaid maid)
                || !MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE)) return;
        MaidMovementControl.end(maid, MaidMovementControl.Reason.CAGE);
        if (!MaidMovementControl.controlsPath(maid)) {
            MaidMovementControl.clearNavigation(maid);
        }
    }

    /** 只在捕获瞬间把实体放到笼内，之后完全依赖笼子的碰撞体限制移动。 */
    private void placeAtCageCenter(LivingEntity living) {
        double x = worldPosition.getX() + 0.5D;
        double floorY = worldPosition.getY();
        double z = worldPosition.getZ() + 0.5D;

        if (living instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.connection.teleport(x, floorY + 0.0625D, z,
                    serverPlayer.getYRot(), serverPlayer.getXRot());
        } else {
            living.setPos(x, floorY + 0.0625D, z);
        }
        living.setNoGravity(false);
        clearCrouchingPose(living);
        living.setDeltaMovement(Vec3.ZERO);
        living.fallDistance = 0.0F;
        rememberSafePosition(living);
    }

    /**
     * 不持续锁死实体：在合法范围内完全放行，只在真正越界时退回最后合法位置。
     * 大型实体按中心计算，因此碰撞箱可以视觉上穿过栏杆，但实体本身无法离开笼子。
     */
    private void keepInsideBoundary(LivingEntity living) {
        boolean logical = usesLogicalContainment(living);
        double halfWidth = living.getBbWidth() * 0.5D;
        double minX = logical ? worldPosition.getX() + 0.5D - LOGICAL_MOVE_RADIUS
                : worldPosition.getX() + INNER_MIN_OFFSET + halfWidth;
        double maxX = logical ? worldPosition.getX() + 0.5D + LOGICAL_MOVE_RADIUS
                : worldPosition.getX() + INNER_MAX_OFFSET - halfWidth;
        double minZ = logical ? worldPosition.getZ() + 0.5D - LOGICAL_MOVE_RADIUS
                : worldPosition.getZ() + INNER_MIN_OFFSET + halfWidth;
        double maxZ = logical ? worldPosition.getZ() + 0.5D + LOGICAL_MOVE_RADIUS
                : worldPosition.getZ() + INNER_MAX_OFFSET - halfWidth;
        if (minX > maxX || minZ > maxZ) {
            minX = worldPosition.getX() + 0.5D - LOGICAL_MOVE_RADIUS;
            maxX = worldPosition.getX() + 0.5D + LOGICAL_MOVE_RADIUS;
            minZ = worldPosition.getZ() + 0.5D - LOGICAL_MOVE_RADIUS;
            maxZ = worldPosition.getZ() + 0.5D + LOGICAL_MOVE_RADIUS;
        }

        double floorY = worldPosition.getY() + INNER_MIN_OFFSET;
        double maxY = Math.max(floorY, worldPosition.getY() + ROOF_HEIGHT - living.getBbHeight());
        boolean inside = living.getX() >= minX && living.getX() <= maxX
                && living.getZ() >= minZ && living.getZ() <= maxZ
                && living.getY() >= floorY - 0.08D && living.getY() <= maxY + 0.08D;
        if (inside) {
            rememberSafePosition(living);
            return;
        }

        double targetX = hasLastSafePosition ? lastSafeX : clamp(living.getX(), minX, maxX);
        double targetY = hasLastSafePosition ? lastSafeY : clamp(living.getY(), floorY, maxY);
        double targetZ = hasLastSafePosition ? lastSafeZ : clamp(living.getZ(), minZ, maxZ);
        if (living instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.connection.teleport(targetX, targetY, targetZ,
                    serverPlayer.getYRot(), serverPlayer.getXRot());
        } else {
            living.setPos(targetX, targetY, targetZ);
        }
        living.setDeltaMovement(0.0D, Math.min(0.0D, living.getDeltaMovement().y), 0.0D);
        if (living instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    private void rememberSafePosition(LivingEntity living) {
        hasLastSafePosition = true;
        lastSafeX = living.getX();
        lastSafeY = living.getY();
        lastSafeZ = living.getZ();
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 空笼主动扫描完整的 1×2 占地。实体的碰撞箱从任意一面刚接触笼子便会被收容，
     * 不再要求先挤过某个没有栏杆碰撞的入口。
     */
    private void captureTouchingEntity(ServerLevel serverLevel) {
        AABB cageBounds = new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                worldPosition.getX() + 1.0D, worldPosition.getY() + 2.0D, worldPosition.getZ() + 1.0D)
                .inflate(TOUCH_MARGIN, 0.02D, TOUCH_MARGIN);
        LivingEntity touching = serverLevel.getEntitiesOfClass(LivingEntity.class, cageBounds,
                        entity -> !(entity instanceof Player) && entity.isAlive()
                                && !entity.isRemoved() && !entity.isSpectator()
                                && entity.getBoundingBox().intersects(cageBounds))
                .stream()
                .min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(worldPosition.getCenter())))
                .orElse(null);
        if (touching != null && capture(touching, null, CageOrigin.NORMAL)) {
            serverLevel.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.IRON_DOOR_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private static void clearCrouchingPose(LivingEntity living) {
        if (living instanceof Player player) {
            player.setShiftKeyDown(false);
            if (player.getPose() == Pose.CROUCHING) {
                player.setPose(Pose.STANDING);
            }
        }
    }

    private Vec3 exitPosition() {
        net.minecraft.core.Direction front = getBlockState().getValue(DarkIronCageBlock.FACING);
        return new Vec3(worldPosition.getX() + 0.5D + front.getStepX() * 2.0D,
                worldPosition.getY() + 0.05D,
                worldPosition.getZ() + 0.5D + front.getStepZ() * 2.0D);
    }

    /** A reciprocal claim prevents two cages from counting down for the same real entity. */
    private boolean canClaimWineFox(WineFoxEntity fox) {
        CompoundTag link = fox.getPersistentData().getCompound(WINE_FOX_CAGE);
        if (!link.contains("Pos") || !link.contains("Dimension")) return true;
        if (ownsWineFoxLink(link)) return true;
        ServerLevel previous = null;
        for (ServerLevel candidate : ((ServerLevel) level).getServer().getAllLevels())
            if (candidate.dimension().location().toString().equals(link.getString("Dimension"))) previous = candidate;
        BlockPos position = BlockPos.of(link.getLong("Pos"));
        if (previous == null || !previous.hasChunkAt(position)) return false; // Unknown is not released.
        return !(previous.getBlockEntity(position) instanceof DarkIronCageBlockEntity cage)
                || !fox.getUUID().equals(cage.occupantId);
    }

    private boolean ownsWineFoxLink(CompoundTag link) {
        return link.contains("Pos") && link.getLong("Pos") == worldPosition.asLong()
                && level.dimension().location().toString().equals(link.getString("Dimension"));
    }

    private void bindWineFox(WineFoxEntity fox) {
        CompoundTag link = new CompoundTag();
        link.putLong("Pos", worldPosition.asLong());
        link.putString("Dimension", level.dimension().location().toString());
        fox.getPersistentData().put(WINE_FOX_CAGE, link);
    }

    private void unbindWineFox(WineFoxEntity fox) {
        if (ownsWineFoxLink(fox.getPersistentData().getCompound(WINE_FOX_CAGE)))
            fox.getPersistentData().remove(WINE_FOX_CAGE);
    }

    private void tickWineFoxEscape(ServerLevel server, WineFoxEntity fox) {
        if (wineFoxEscapeState == WineFoxEscapeState.RELEASED || isRemoved()) return;
        if (!canClaimWineFox(fox)) { clearOccupant(); return; }
        if (wineFoxEscapeState == WineFoxEscapeState.NONE) {
            // Existing world cages already support this entity; initialize only their missing new timer.
            bindWineFox(fox);
            wineFoxEscapeState = WineFoxEscapeState.CAPTURED;
            wineFoxEscapeTicks = WINE_FOX_ESCAPE_TICKS;
        }
        if (lastWineFoxTick == server.getGameTime()) return;
        lastWineFoxTick = server.getGameTime();
        if (wineFoxEscapeState == WineFoxEscapeState.CAPTURED && --wineFoxEscapeTicks > 0) {
            setChanged(); return;
        }
        wineFoxEscapeTicks = 0;
        wineFoxEscapeState = WineFoxEscapeState.ESCAPING;
        setChanged();
        if (wineFoxRetryTicks > 0) { wineFoxRetryTicks--; return; }
        Vec3 exit = safeWineFoxExit(server, fox);
        if (exit == null) { wineFoxRetryTicks = WINE_FOX_RETRY_TICKS; return; }
        clearGoldenAppleEffects(fox);
        boolean gravity = occupantWasNoGravity;
        unbindWineFox(fox);
        fox.getNavigation().stop();
        fox.setPos(exit.x, exit.y, exit.z); // Same object/UUID: never serialize, spawn or finalizeSpawn.
        fox.setDeltaMovement(Vec3.ZERO);
        fox.setNoGravity(gravity);
        fox.fallDistance = 0;
        clearOccupant(); // onRemove must see no prisoner, so it cannot move/release her a second time.
        wineFoxEscapeState = WineFoxEscapeState.RELEASED;
        setChanged();
        server.setBlock(worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL | net.minecraft.world.level.block.Block.UPDATE_SUPPRESS_DROPS);
        fox.cageEscapeDialogue(); // Bubble only, exactly once after this capture's successful release.
    }

    private @Nullable Vec3 safeWineFoxExit(ServerLevel server, WineFoxEntity fox) {
        int[] heights = {0, 1, -1, 2, -2, 3, -3, 4, -4};
        for (int radius = 1; radius <= 6; radius++)
            for (int dy : heights)
                for (int dx = -radius; dx <= radius; dx++)
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        BlockPos feet = worldPosition.offset(dx, dy, dz);
                        Vec3 exit = new Vec3(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
                        AABB box = fox.getBoundingBox().move(exit.subtract(fox.position()));
                        BlockPos low = BlockPos.containing(box.minX, box.minY - 1, box.minZ);
                        BlockPos high = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
                        if (box.minY <= server.getMinBuildHeight() || box.maxY >= server.getMaxBuildHeight()
                                || !server.hasChunksAt(low, high) || !server.getWorldBorder().isWithinBounds(box)
                                || !server.loadedAndEntityCanStandOn(feet.below(), fox)
                                || !server.noCollision(fox, box)) continue;
                        boolean safe = true;
                        for (BlockPos p : BlockPos.betweenClosed(low, high)) {
                            BlockState state = server.getBlockState(p);
                            if (!state.getFluidState().isEmpty() || state.is(ModBlocks.DARK_IRON_CAGE.get())
                                    || state.is(net.minecraft.tags.BlockTags.FIRE)
                                    || state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                                    || state.is(net.minecraft.world.level.block.Blocks.CACTUS)
                                    || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                                    || state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                                    || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                                    || net.minecraft.world.level.block.CampfireBlock.isLitCampfire(state)) { safe = false; break; }
                        }
                        if (safe) return exit;
                    }
        return null;
    }

    private void applyEnvironment(ServerLevel serverLevel, LivingEntity living, CageEnvironment environment) {
        switch (environment) {
            case EMPTY -> { }
            case WATER -> {
                living.clearFire();
                if (!living.canBreatheUnderwater() && !living.hasEffect(MobEffects.WATER_BREATHING)) {
                    // 实体并未真的处于水方块中，原版每 tick 会回 4 点空气，因此扣 5 才能净减少 1。
                    int air = living.getAirSupply() - 5;
                    living.setAirSupply(air);
                    if (air <= -20) {
                        living.setAirSupply(0);
                        living.hurt(level.damageSources().drown(), 2.0F);
                    }
                }
            }
            case LAVA -> {
                living.igniteForSeconds(15);
                if (living.tickCount % 10 == 0) {
                    living.hurt(level.damageSources().lava(), 4.0F);
                }
            }
            case POWDER_SNOW -> {
                // 同理抵消“不在细雪中”时原版的自然解冻，再保持每 tick 净增加。
                living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze(), living.getTicksFrozen() + 3));
                if (living.isFullyFrozen() && living.tickCount % 40 == 0) {
                    living.hurt(level.damageSources().freeze(), 1.0F);
                }
            }
            case FIRE -> {
                living.igniteForSeconds(2);
                if (living.tickCount % 20 == 0) {
                    living.hurt(serverLevel.damageSources().inFire(), 1.0F);
                }
            }
            case CACTUS -> {
                if (living.tickCount % 10 == 0) {
                    living.hurt(serverLevel.damageSources().cactus(), 1.0F);
                }
            }
            case LIGHTNING -> tickLightning(serverLevel, living);
            case GOLDEN_APPLE -> maintainGoldenAppleEffects(serverLevel, living);
        }
    }

    private void trackEnvironment(ServerLevel serverLevel, CageEnvironment environment) {
        if (trackedEnvironment == environment) return;
        if (environment != CageEnvironment.GOLDEN_APPLE) {
            clearGoldenAppleEffects(goldenAppleTarget(serverLevel));
        }
        trackedEnvironment = environment;
        nextLightningTime = environment == CageEnvironment.LIGHTNING
                ? serverLevel.getGameTime() + LIGHTNING_INTERVAL : 0L;
        setChanged();
    }

    private void tickLightning(ServerLevel serverLevel, LivingEntity living) {
        long now = serverLevel.getGameTime();
        if (nextLightningTime <= 0L) {
            nextLightningTime = now + LIGHTNING_INTERVAL;
            setChanged();
            return;
        }
        if (now < nextLightningTime) return;
        net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (lightning != null) {
            lightning.moveTo(living.getX(), living.getY(), living.getZ());
            // 视觉闪电不扫描周围实体；原版雷击效果只显式结算给当前囚犯。
            lightning.setVisualOnly(true);
            serverLevel.addFreshEntity(lightning);
            living.thunderHit(serverLevel, lightning);
        }
        nextLightningTime = now + LIGHTNING_INTERVAL;
        setChanged();
    }

    private void maintainGoldenAppleEffects(ServerLevel serverLevel, LivingEntity living) {
        boolean trackerChanged = false;
        if (!living.getUUID().equals(goldenAppleTargetId)) {
            clearGoldenAppleEffects(goldenAppleTarget(serverLevel));
            goldenAppleTargetId = living.getUUID();
            trackerChanged = true;
        }
        if (!ownsRegeneration) {
            MobEffectInstance current = living.getEffect(MobEffects.REGENERATION);
            if (current == null || current.getAmplifier() < 2) {
                previousRegeneration = current == null ? null : new MobEffectInstance(current);
                regenerationClaimTime = serverLevel.getGameTime();
                ownsRegeneration = true;
                trackerChanged = true;
            }
        }
        if (!ownsResistance) {
            MobEffectInstance current = living.getEffect(MobEffects.DAMAGE_RESISTANCE);
            if (current == null) {
                previousResistance = null;
                resistanceClaimTime = serverLevel.getGameTime();
                ownsResistance = true;
                trackerChanged = true;
            }
        }
        boolean regenerationWasOwned = ownsRegeneration;
        boolean resistanceWasOwned = ownsResistance;
        ownsRegeneration = maintainOwnedEffect(living, MobEffects.REGENERATION, 2, ownsRegeneration);
        ownsResistance = maintainOwnedEffect(living, MobEffects.DAMAGE_RESISTANCE, 0, ownsResistance);
        if (trackerChanged || regenerationWasOwned != ownsRegeneration
                || resistanceWasOwned != ownsResistance) {
            setChanged();
        }
    }

    private static boolean maintainOwnedEffect(LivingEntity living, Holder<MobEffect> effect,
                                               int amplifier, boolean owned) {
        if (!owned) return false;
        MobEffectInstance current = living.getEffect(effect);
        if (current != null && (current.getAmplifier() > amplifier
                || current.getAmplifier() == amplifier && current.getDuration() > GOLDEN_EFFECT_DURATION)) {
            return false;
        }
        if (current == null || current.getAmplifier() < amplifier || current.getDuration() <= 20) {
            living.addEffect(new MobEffectInstance(effect, GOLDEN_EFFECT_DURATION, amplifier));
        }
        return true;
    }

    private void clearGoldenAppleEffects(@Nullable LivingEntity living) {
        if (goldenAppleTargetId == null && !ownsRegeneration && !ownsResistance) return;
        if (living != null && living.getUUID().equals(goldenAppleTargetId) && level != null) {
            restoreOwnedEffect(living, MobEffects.REGENERATION, 2, ownsRegeneration,
                    previousRegeneration, regenerationClaimTime, level.getGameTime());
            restoreOwnedEffect(living, MobEffects.DAMAGE_RESISTANCE, 0, ownsResistance,
                    previousResistance, resistanceClaimTime, level.getGameTime());
        }
        goldenAppleTargetId = null;
        ownsRegeneration = false;
        ownsResistance = false;
        previousRegeneration = null;
        previousResistance = null;
        regenerationClaimTime = 0L;
        resistanceClaimTime = 0L;
        setChanged();
    }

    private static void restoreOwnedEffect(LivingEntity living, Holder<MobEffect> effect, int amplifier,
                                           boolean owned, @Nullable MobEffectInstance previous,
                                           long claimTime, long now) {
        if (!owned) return;
        MobEffectInstance current = living.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier
                && current.getDuration() <= GOLDEN_EFFECT_DURATION) {
            living.removeEffect(effect);
        }
        if (previous == null || living.hasEffect(effect)) return;
        int remaining = previous.getDuration() - (int) Math.max(0L, now - claimTime);
        if (remaining > 0) {
            living.addEffect(new MobEffectInstance(previous.getEffect(), remaining,
                    previous.getAmplifier(), previous.isAmbient(), previous.isVisible(), previous.showIcon()));
        }
    }

    private @Nullable LivingEntity goldenAppleTarget(ServerLevel serverLevel) {
        if (goldenAppleTargetId == null) return null;
        Entity entity = serverLevel.getEntity(goldenAppleTargetId);
        return entity instanceof LivingEntity living ? living : null;
    }

    /** 仅自然结构中仍被本铁笼收容的女仆会定时求救，不调用 AI。 */
    private void tickPrisonerSpeech(ServerLevel serverLevel, LivingEntity living) {
        if (!(living instanceof EntityMaid maid) || origin == CageOrigin.NORMAL || WineFoxSurrender.isSurrendered(maid)) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (nextPrisonerSpeechTime <= 0L) {
            nextPrisonerSpeechTime = now + PRISONER_SPEECH_INTERVAL + maid.getRandom().nextInt(101);
            setChanged();
            return;
        }
        if (now < nextPrisonerSpeechTime) {
            return;
        }
        String group = switch (origin) {
            case VILLAGE -> "village";
            case PILLAGER_OUTPOST -> "outpost";
            case STRONGHOLD -> "stronghold";
            case NORMAL -> "";
        };
        int line = 1 + maid.getRandom().nextInt(PRISONER_SPEECH_COUNT);
        maid.getChatBubbleManager().addTextChatBubble("bubble.callresponse.cage." + group + "." + line);
        nextPrisonerSpeechTime = now + PRISONER_SPEECH_INTERVAL + maid.getRandom().nextInt(101);
        setChanged();
    }

    /** 背叛女仆入笼后一件件卸下全部装备，并以固定语言键定时放狠话。 */
    private void tickBetrayalMaid(ServerLevel serverLevel, LivingEntity living) {
        if (!(living instanceof EntityMaid maid) || !EmotionBetrayalManager.isBetraying(maid) || WineFoxSurrender.isSurrendered(maid)) {
            return;
        }
        long now = serverLevel.getGameTime();
        if (!betrayalStrippingComplete && now >= nextBetrayalStripTime) {
            betrayalStrippingComplete = !dropNextBetrayalItem(serverLevel, maid);
            nextBetrayalStripTime = now + BETRAYAL_STRIP_INTERVAL;
            setChanged();
        }
        if (now >= nextBetrayalSpeechTime) {
            int line = 1 + maid.getRandom().nextInt(BETRAYAL_SPEECH_COUNT);
            maid.getChatBubbleManager().addTextChatBubble("bubble.callresponse.cage.betrayal." + line);
            nextBetrayalSpeechTime = now + BETRAYAL_SPEECH_INTERVAL + maid.getRandom().nextInt(201);
            setChanged();
        }
    }

    private boolean dropNextBetrayalItem(ServerLevel serverLevel, EntityMaid maid) {
        EquipmentSlot[] equipment = {
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };
        for (EquipmentSlot slot : equipment) {
            ItemStack stack = maid.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                maid.setItemSlot(slot, ItemStack.EMPTY);
                dropAwayFromCage(serverLevel, maid, stack.copy());
                return true;
            }
        }
        if (dropNextFromHandler(serverLevel, maid, maid.getMaidInv())) return true;
        return dropNextFromHandler(serverLevel, maid, maid.getMaidBauble());
    }

    private boolean dropNextFromHandler(ServerLevel serverLevel, EntityMaid maid, IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            ItemStack extracted = handler.extractItem(slot, stack.getCount(), false);
            if (!extracted.isEmpty()) {
                dropAwayFromCage(serverLevel, maid, extracted);
                return true;
            }
        }
        return false;
    }

    private void dropAwayFromCage(ServerLevel serverLevel, EntityMaid maid, ItemStack stack) {
        double angle = maid.getRandom().nextDouble() * Math.PI * 2.0D;
        double radius = 1.6D + maid.getRandom().nextDouble() * 0.4D;
        ItemEntity item = new ItemEntity(serverLevel,
                worldPosition.getX() + 0.5D + Math.cos(angle) * radius,
                worldPosition.getY() + 0.25D,
                worldPosition.getZ() + 0.5D + Math.sin(angle) * radius, stack);
        item.setPickUpDelay(80);
        serverLevel.addFreshEntity(item);
    }

    /** 数值只在入笼时结算；持久化冷却避免反复进出刷情绪。 */
    private void applyCageEmotion(LivingEntity living, @Nullable Player captor) {
        if (!(level instanceof ServerLevel serverLevel) || !(living instanceof EntityMaid maid)) return;
        long now = serverLevel.getGameTime();
        UUID relationId = captor != null ? captor.getUUID() : maid.getOwnerUUID();
        if (relationId == null) {
            Player nearest = serverLevel.getNearestPlayer(maid, 16.0D);
            if (nearest != null) relationId = nearest.getUUID();
        }
        long ownUntil = maid.getPersistentData().getLong(CAGE_EMOTION_UNTIL);
        if (relationId != null && (now >= ownUntil || now < ownUntil - CAGE_EMOTION_COOLDOWN)) {
            EmotionData.addFear(maid, relationId, 6);
            EmotionData.addTrust(maid, relationId, -1);
            maid.getPersistentData().putLong(CAGE_EMOTION_UNTIL, now + CAGE_EMOTION_COOLDOWN);
        }

        for (EntityMaid witness : serverLevel.getEntitiesOfClass(EntityMaid.class,
                maid.getBoundingBox().inflate(10.0D), other -> other != maid && other.isAlive())) {
            if (JealousyCageManager.isCageWitnessFearExempt(witness)) continue;
            UUID witnessOwner = witness.getOwnerUUID();
            long witnessUntil = witness.getPersistentData().getLong(CAGE_WITNESS_UNTIL);
            if (witnessOwner != null && (now >= witnessUntil
                    || now < witnessUntil - CAGE_EMOTION_COOLDOWN)) {
                EmotionData.addFear(witness, witnessOwner, 4);
                witness.getPersistentData().putLong(CAGE_WITNESS_UNTIL, now + CAGE_EMOTION_COOLDOWN);
            }
        }
    }

    private void clearOccupant() {
        clearGoldenAppleEffects(occupant());
        if (occupant() instanceof WineFoxEntity fox) unbindWineFox(fox);
        wineFoxEscapeState = WineFoxEscapeState.NONE;
        wineFoxEscapeTicks = wineFoxRetryTicks = 0;
        lastWineFoxTick = Long.MIN_VALUE;
        occupantId = null;
        occupantName = Component.empty();
        captorId = null;
        captorName = Component.empty();
        missingEntityTicks = 0;
        occupantWasNoGravity = false;
        nextPrisonerSpeechTime = 0L;
        nextBetrayalStripTime = 0L;
        nextBetrayalSpeechTime = 0L;
        betrayalStrippingComplete = false;
        hasLastSafePosition = false;
        rehabilitationRetryDelay = 0;
        syncOccupied(false);
        setChanged();
    }

    private void syncOccupied(boolean occupied) {
        if (level == null) {
            return;
        }
        DarkIronCageBlock.updateBothHalves(level, worldPosition,
                state -> state.setValue(DarkIronCageBlock.OCCUPIED, occupied));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("WineFoxEscapeState", wineFoxEscapeState.name());
        tag.putInt("WineFoxEscapeTicks", wineFoxEscapeTicks);
        tag.putInt("WineFoxRetryTicks", wineFoxRetryTicks);
        if (occupantId != null) tag.putUUID("Occupant", occupantId);
        if (!occupantName.getString().isBlank()) tag.putString("OccupantName", Component.Serializer.toJson(occupantName, provider));
        if (captorId != null) tag.putUUID("Captor", captorId);
        if (placerId != null) tag.putUUID("Placer", placerId);
        if (!captorName.getString().isBlank()) tag.putString("CaptorName", Component.Serializer.toJson(captorName, provider));
        tag.putString("Origin", origin.name());
        tag.putBoolean("OccupantWasNoGravity", occupantWasNoGravity);
        tag.putLong("NextPrisonerSpeechTime", nextPrisonerSpeechTime);
        tag.putLong("NextBetrayalStripTime", nextBetrayalStripTime);
        tag.putLong("NextBetrayalSpeechTime", nextBetrayalSpeechTime);
        tag.putBoolean("BetrayalStrippingComplete", betrayalStrippingComplete);
        tag.putString("TrackedEnvironment", trackedEnvironment.getSerializedName());
        tag.putLong("NextLightningTime", nextLightningTime);
        if (goldenAppleTargetId != null) tag.putUUID("GoldenAppleTarget", goldenAppleTargetId);
        tag.putBoolean("OwnsRegeneration", ownsRegeneration);
        tag.putBoolean("OwnsResistance", ownsResistance);
        tag.putLong("RegenerationClaimTime", regenerationClaimTime);
        tag.putLong("ResistanceClaimTime", resistanceClaimTime);
        if (previousRegeneration != null) tag.put("PreviousRegeneration", previousRegeneration.save());
        if (previousResistance != null) tag.put("PreviousResistance", previousResistance.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        wineFoxEscapeState = WineFoxEscapeState.NONE;
        for (WineFoxEscapeState state : WineFoxEscapeState.values())
            if (state.name().equals(tag.getString("WineFoxEscapeState"))) wineFoxEscapeState = state;
        wineFoxEscapeTicks = net.minecraft.util.Mth.clamp(tag.getInt("WineFoxEscapeTicks"), 0, WINE_FOX_ESCAPE_TICKS);
        wineFoxRetryTicks = net.minecraft.util.Mth.clamp(tag.getInt("WineFoxRetryTicks"), 0, WINE_FOX_RETRY_TICKS);
        lastWineFoxTick = Long.MIN_VALUE;
        occupantId = tag.hasUUID("Occupant") ? tag.getUUID("Occupant") : null;
        occupantName = readComponent(tag, "OccupantName");
        captorId = tag.hasUUID("Captor") ? tag.getUUID("Captor") : null;
        placerId = tag.hasUUID("Placer") ? tag.getUUID("Placer") : null;
        captorName = readComponent(tag, "CaptorName");
        origin = CageOrigin.byName(tag.getString("Origin"));
        occupantWasNoGravity = tag.getBoolean("OccupantWasNoGravity");
        nextPrisonerSpeechTime = tag.getLong("NextPrisonerSpeechTime");
        nextBetrayalStripTime = tag.getLong("NextBetrayalStripTime");
        nextBetrayalSpeechTime = tag.getLong("NextBetrayalSpeechTime");
        betrayalStrippingComplete = tag.getBoolean("BetrayalStrippingComplete");
        trackedEnvironment = environmentByName(tag.getString("TrackedEnvironment"),
                getBlockState().getValue(DarkIronCageBlock.ENVIRONMENT));
        nextLightningTime = tag.getLong("NextLightningTime");
        goldenAppleTargetId = tag.hasUUID("GoldenAppleTarget") ? tag.getUUID("GoldenAppleTarget") : null;
        ownsRegeneration = tag.getBoolean("OwnsRegeneration");
        ownsResistance = tag.getBoolean("OwnsResistance");
        regenerationClaimTime = tag.getLong("RegenerationClaimTime");
        resistanceClaimTime = tag.getLong("ResistanceClaimTime");
        previousRegeneration = tag.contains("PreviousRegeneration", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? MobEffectInstance.load(tag.getCompound("PreviousRegeneration")) : null;
        previousResistance = tag.contains("PreviousResistance", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? MobEffectInstance.load(tag.getCompound("PreviousResistance")) : null;
        missingEntityTicks = 0;
    }

    private static CageEnvironment environmentByName(String name, CageEnvironment fallback) {
        for (CageEnvironment environment : CageEnvironment.values()) {
            if (environment.getSerializedName().equals(name)) return environment;
        }
        return fallback;
    }

    private static Component readComponent(CompoundTag tag, String key) {
        if (!tag.contains(key)) return Component.empty();
        Component component = Component.Serializer.fromJson(tag.getString(key),
                net.minecraft.core.RegistryAccess.EMPTY);
        return component == null ? Component.empty() : component;
    }
}
