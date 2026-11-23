package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.outpost.entity.OutpostEntities;
import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One bounded spawn transaction at the player's latest personal respawn, preserving allegiance rules. */
@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class WineFoxPursuit {
    public static final double TRIGGER_RANGE = 10;
    private static final double MIN_PLAYER_SPAWN_DISTANCE = 10;
    private static final String EVENT = "CallResponseWineFoxPursuit";
    private static final int CHECK_TICKS = 10, RETRY_TICKS = 40, JOIN_BATCH = 8;
    private static final int INITIAL_SEARCH_RADIUS = 10, SEARCH_RADIUS = 16, SEARCH_HEIGHT = 8;
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Set<ServerPlayer> REQUESTS = new HashSet<>();
    private static final Map<UUID, Long> NEXT_SEARCH = new HashMap<>();
    private static long nextFailureWarning;

    /** Retained zero-based amplifier API; the runtime passes the independently saved level minus one. */
    public static int followerCount(int amplifier) {
        long displayLevel = Math.max(1L, (long) amplifier + 1L);
        return (int) Math.min(32L, 5L + Math.max(0L, displayLevel - 3L));
    }

    public static boolean isPreferredTarget(Entity actor, Entity candidate) {
        CompoundTag event = actor.getPersistentData().getCompound(EVENT);
        return !event.getBoolean("Pending") && candidate != null && event.hasUUID("Player")
                && candidate.getUUID().equals(event.getUUID("Player"));
    }

    @SubscribeEvent public static void effectAdded(MobEffectEvent.Added event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getEffectInstance().getEffect() == WineFoxEffects.CORRUPTED_HUNT.get()) {
            // Added is dispatched before vanilla finishes storing/merging the effect.
            requestCheck(player);
        }
    }

    /** Run after the granting call has finished writing the actual effect and saved level. */
    public static void requestCheck(ServerPlayer player) {
        REQUESTS.add(player);
        NEXT_SEARCH.remove(player.getUUID());
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) REQUESTS.add(player);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            REQUESTS.remove(player);
            NEXT_SEARCH.remove(player.getUUID());
            for (Pending pending : List.copyOf(PENDING.values()))
                if (pending.player == player) rollback(pending);
        }
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && Math.floorMod(player.tickCount + player.getId(), CHECK_TICKS) == 0
                && player.serverLevel().getGameTime() >= NEXT_SEARCH.getOrDefault(player.getUUID(), 0L))
            tryTrigger(player);
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerPlayer player : List.copyOf(REQUESTS)) {
            if (player.getServer() != event.getServer()) continue;
            REQUESTS.remove(player);
            if (!player.isRemoved()) tryTrigger(player);
        }
        for (Pending pending : List.copyOf(PENDING.values()))
            if (pending.level.getServer() == event.getServer()) advance(pending);
    }

    /** True means the complete team committed. False leaves the effect, including while a batch is pending. */
    public static boolean tryTrigger(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        MobEffectInstance effect = player.getEffect(WineFoxEffects.CORRUPTED_HUNT.get());
        BlockPos spawn = player.getRespawnPosition();
        if (spawn == null || !level.dimension().equals(player.getRespawnDimension())
                || effect == null || !player.isAlive() || player.isRemoved() || player.isSpectator()
                || level.getDifficulty() == Difficulty.PEACEFUL
                || player.position().distanceToSqr(Vec3.atCenterOf(spawn)) > TRIGGER_RANGE * TRIGGER_RANGE)
            return false;
        for (Pending pending : PENDING.values()) if (pending.player == player) return false;
        WineFoxPursuitData events = WineFoxPursuitData.get(level);
        events.releaseConfirmedDead(WineFoxLeaders.get(level));
        String dimension = level.dimension().location().toString();
        if (events.isOccupied(dimension, spawn)) return false;
        NEXT_SEARCH.put(player.getUUID(), level.getGameTime() + RETRY_TICKS);

        int maliceLevel = WineFoxPotions.getMaliceLevel(player);
        int count = followerCount(maliceLevel - 1);
        BlockPos anchor = findAnchor(level, player, spawn);
        if (anchor == null) return false;
        WineFoxEntity leader = WineFoxEntities.FOX.get().create(level);
        if (leader == null) return false;
        Pending pending = new Pending(player, level, effect, maliceLevel, count, spawn, leader,
                positions(level, player, anchor, count + 1));
        try {
            initialize(leader, level, pending.positions.get(0));
            markPending(pending, leader);
            if (!events.reserve(leader.getUUID(), dimension, spawn, player.getUUID())) {
                leader.discard();
                return false;
            }
            WineFoxSpawnStats.initializeSpawn(leader, player);
            PENDING.put(leader.getUUID(), pending);
            return advance(pending);
        } catch (RuntimeException failure) {
            warn(player, failure);
            rollback(pending);
            return false;
        }
    }

    private static final class Pending {
        final ServerPlayer player;
        final ServerLevel level;
        final MobEffectInstance effect;
        final int maliceLevel, followers;
        final BlockPos spawn;
        final Vec3 hornPosition;
        final WineFoxEntity leader;
        final List<Vec3> positions;
        final List<Mob> members = new ArrayList<>();
        int nextFollower;
        long lastTick = Long.MIN_VALUE;
        Pending(ServerPlayer player, ServerLevel level, MobEffectInstance effect, int maliceLevel,
                int followers, BlockPos spawn, WineFoxEntity leader, List<Vec3> positions) {
            this.player = player; this.level = level; this.effect = effect; this.maliceLevel = maliceLevel;
            this.followers = followers; this.spawn = spawn.immutable(); this.leader = leader;
            this.positions = positions; this.hornPosition = player.position();
            members.add(leader);
        }
    }

    private static boolean advance(Pending pending) {
        if (pending.lastTick == pending.level.getGameTime()) return false;
        pending.lastTick = pending.level.getGameTime();
        boolean committed = false;
        boolean keepPending = false;
        try {
            ServerPlayer player = pending.player;
            ServerLevel level = pending.level;
            if (!player.isAlive() || player.isRemoved() || player.isSpectator() || player.level() != level
                    || !level.dimension().equals(player.getRespawnDimension())
                    || !pending.spawn.equals(player.getRespawnPosition())
                    || level.getDifficulty() == Difficulty.PEACEFUL
                    || player.getEffect(WineFoxEffects.CORRUPTED_HUNT.get()) != pending.effect
                    || WineFoxPotions.getMaliceLevel(player) != pending.maliceLevel) return false;
            for (Mob member : pending.members) if (member.isRemoved() || !member.isAlive()) return false;
            int joined = 0;
            while (pending.nextFollower < pending.followers && joined < JOIN_BATCH) {
                RevengeMaidEntity maid = OutpostEntities.REVENGE_MAID.get().create(level);
                if (maid == null) return false;
                pending.members.add(maid);
                initialize(maid, level, pending.positions.get(pending.nextFollower + 1));
                markPending(pending, maid);
                if (!WineFoxFaction.bind(pending.leader, maid)
                        || !safeForMember(pending, maid) || !level.addFreshEntity(maid)
                        || maid.isRemoved() || !maid.isAlive()) return false;
                pending.nextFollower++;
                joined++;
            }
            if (pending.nextFollower < pending.followers || joined >= JOIN_BATCH) {
                keepPending = true;
                return false;
            }
            // Add the leader last: she cannot recruit unrelated maids while a partial batch exists.
            if (!safeForMember(pending, pending.leader) || !level.addFreshEntity(pending.leader)
                    || pending.leader.isRemoved() || !pending.leader.isAlive()) return false;
            // Revalidate moved players/changed blocks for every already added member before commit.
            for (Mob member : pending.members)
                if (!member.isAlive() || member.isRemoved() || !safeForMember(pending, member)) return false;
            if (player.getEffect(WineFoxEffects.CORRUPTED_HUNT.get()) != pending.effect
                    || !player.removeEffect(WineFoxEffects.CORRUPTED_HUNT.get())) return false;
            WineFoxPotions.clearMaliceLevel(player);
            WineFoxPursuitData.get(level).commit(pending.leader.getUUID());
            pending.leader.setGlowingTag(true);
            for (Mob member : pending.members) finishPending(member);
            PENDING.remove(pending.leader.getUUID());
            NEXT_SEARCH.remove(player.getUUID());
            committed = true;
            WineFoxDialogue.announceArrival(pending.leader, player);
            level.playSound(null, pending.hornPosition.x, pending.hornPosition.y, pending.hornPosition.z,
                    SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 1.0F);
            return true;
        } catch (RuntimeException failure) {
            warn(pending.player, failure);
            return false;
        } finally {
            if (!committed && !keepPending) rollback(pending);
        }
    }

    private static void markPending(Pending pending, Mob member) {
        CompoundTag identity = new CompoundTag();
        identity.putUUID("Player", pending.player.getUUID());
        identity.putUUID("Leader", pending.leader.getUUID());
        identity.putString("Dimension", pending.level.dimension().location().toString());
        identity.putLong("Respawn", pending.spawn.asLong()); // Retain existing saved key for compatibility.
        identity.putBoolean("Pending", true);
        identity.putBoolean("PreviousNoAI", member.isNoAi());
        identity.putBoolean("PreviousInvulnerable", member.isInvulnerable());
        identity.putBoolean("RaidGlowing", member == pending.leader);
        member.getPersistentData().put(EVENT, identity);
        member.setNoAi(true);
        member.setInvulnerable(true);
    }

    private static void finishPending(Mob member) {
        CompoundTag identity = member.getPersistentData().getCompound(EVENT);
        if (identity.getBoolean("Pending")) {
            member.setNoAi(identity.getBoolean("PreviousNoAI"));
            member.setInvulnerable(identity.getBoolean("PreviousInvulnerable"));
            identity.remove("Pending");
            identity.remove("PreviousNoAI");
            identity.remove("PreviousInvulnerable");
        }
        if (member instanceof WineFoxEntity && identity.getBoolean("RaidGlowing")) member.setGlowingTag(true);
    }

    static boolean pending(Entity entity) {
        return entity.getPersistentData().getCompound(EVENT).getBoolean("Pending");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pendingTick(LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide && pending(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void pendingAttack(LivingAttackEvent event) {
        if (pending(event.getEntity())) event.setCanceled(true);
    }

    private static void rollback(Pending pending) {
        PENDING.remove(pending.leader.getUUID());
        for (Mob member : pending.members) if (!member.isRemoved()) member.discard();
        WineFoxPursuitData.get(pending.level).release(pending.leader.getUUID());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        for (Pending pending : List.copyOf(PENDING.values()))
            if (pending.level.getServer() == server) rollback(pending);
        REQUESTS.removeIf(player -> player.getServer() == server);
        NEXT_SEARCH.clear();
    }

    private static void warn(ServerPlayer player, RuntimeException failure) {
        long now = System.nanoTime();
        if (nextFailureWarning != 0 && now - nextFailureWarning < 0) return;
        nextFailureWarning = now + 60_000_000_000L;
        CallResponseMod.LOGGER.warn("Wine fox pursuit spawn rolled back for player {} (warnings limited to once per minute)",
                player.getUUID(), failure);
    }

    private static void initialize(Mob mob, ServerLevel level, Vec3 position) {
        mob.moveTo(position.x, position.y, position.z, level.getRandom().nextFloat() * 360, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null, null);
        mob.setPersistenceRequired();
    }

    private static AABB teamBox(Vec3 position) {
        double width = Math.max(WineFoxEntities.FOX.get().getWidth(), OutpostEntities.REVENGE_MAID.get().getWidth());
        double height = Math.max(WineFoxEntities.FOX.get().getHeight(), OutpostEntities.REVENGE_MAID.get().getHeight());
        return new AABB(position.x - width / 2, position.y, position.z - width / 2,
                position.x + width / 2, position.y + height, position.z + width / 2);
    }

    private static BlockPos findAnchor(ServerLevel level, ServerPlayer player, BlockPos spawn) {
        // The player's latest PERSONAL respawn is the sole origin, never the player's head/position.
        // Finish a complete same-height plane before trying higher planes, then lower planes.
        int minimumRadius = 0;
        for (int maximumRadius = INITIAL_SEARCH_RADIUS; maximumRadius <= SEARCH_RADIUS; maximumRadius += 2) {
            BlockPos found = findAnchorPlane(level, player, spawn, minimumRadius, maximumRadius, 0);
            if (found != null) return found;
            for (int dy = 1; dy <= SEARCH_HEIGHT; dy++) {
                found = findAnchorPlane(level, player, spawn, minimumRadius, maximumRadius, dy);
                if (found != null) return found;
            }
            for (int dy = 1; dy <= SEARCH_HEIGHT; dy++) {
                found = findAnchorPlane(level, player, spawn, minimumRadius, maximumRadius, -dy);
                if (found != null) return found;
            }
            minimumRadius = maximumRadius + 1;
        }
        return null;
    }

    private static BlockPos findAnchorPlane(ServerLevel level, ServerPlayer player, BlockPos origin,
                                            int minimumRadius, int maximumRadius, int dy) {
        for (int radius = minimumRadius; radius <= maximumRadius; radius++)
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos feet = origin.offset(dx, dy, dz);
                    if (safePosition(level, player, feet, teamBox(Vec3.atBottomCenterOf(feet)), Set.of())) return feet;
                }
        return null;
    }

    private static List<Vec3> positions(ServerLevel level, ServerPlayer player, BlockPos anchor, int needed) {
        List<BlockPos> floors = new ArrayList<>();
        floors.add(anchor);
        for (int radius = 1; radius <= 4 && floors.size() < needed; radius++)
            for (int dx = -radius; dx <= radius && floors.size() < needed; dx++)
                for (int dz = -radius; dz <= radius && floors.size() < needed; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy : new int[]{0, 1, -1, 2, -2}) {
                        BlockPos floor = anchor.offset(dx, dy, dz);
                        if (safePosition(level, player, floor, teamBox(Vec3.atBottomCenterOf(floor)), Set.of())) {
                            floors.add(floor); break;
                        }
                    }
                }
        List<Vec3> result = new ArrayList<>(needed);
        result.add(Vec3.atBottomCenterOf(anchor));
        for (int i = 1; i < needed; i++) {
            BlockPos floor = floors.get(i % floors.size());
            Vec3 center = Vec3.atBottomCenterOf(floor);
            double angle = i * 2.399963229728653;
            Vec3 offset = center.add(Math.cos(angle) * .1, 0, Math.sin(angle) * .1);
            result.add(safePosition(level, player, floor, teamBox(offset), Set.of()) ? offset : center);
        }
        return result;
    }

    private static boolean safeForMember(Pending pending, Mob member) {
        Set<UUID> team = new HashSet<>();
        for (Mob sibling : pending.members) team.add(sibling.getUUID());
        return safePosition(pending.level, pending.player, member.blockPosition(), member.getBoundingBox(), team);
    }

    private static boolean safePosition(ServerLevel level, ServerPlayer trigger, BlockPos feet, AABB bounds, Set<UUID> team) {
        BlockPos low = BlockPos.containing(bounds.minX, bounds.minY - 1, bounds.minZ);
        BlockPos high = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        if (bounds.minY <= level.getMinBuildHeight() || bounds.maxY >= level.getMaxBuildHeight()
                || !level.hasChunksAt(low, high) || !level.getWorldBorder().isWithinBounds(bounds)) return false;
        BlockPos below = feet.below();
        var floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || floor.is(BlockTags.LEAVES)) return false;
        Vec3 center = bounds.getCenter();
        if (insideSpawnBuffer(trigger, center)) return false;
        for (ServerPlayer player : level.players()) {
            if (player != trigger && player.isAlive() && !player.isSpectator()
                    && insideSpawnBuffer(player, center)) return false;
        }
        for (BlockPos check : BlockPos.betweenClosed(low, high)) {
            var block = level.getBlockState(check);
            if (!level.getFluidState(check).isEmpty() || block.is(BlockTags.FIRE)
                    || block.is(Blocks.MAGMA_BLOCK) || block.is(Blocks.CACTUS) || block.is(Blocks.WITHER_ROSE)
                    || block.is(Blocks.SWEET_BERRY_BUSH) || block.is(Blocks.POWDER_SNOW)
                    || block.is(Blocks.POINTED_DRIPSTONE) || CampfireBlock.isLitCampfire(block)) return false;
        }
        for (var collision : level.getBlockCollisions(null, bounds)) if (!collision.isEmpty()) return false;
        if (trigger.isAlive() && trigger.getBoundingBox().intersects(bounds)) return false;
        // Do not ignore all entity collision globally: only members of this very transaction may overlap.
        return level.getEntitiesOfClass(LivingEntity.class, bounds,
                entity -> entity.isAlive() && !team.contains(entity.getUUID())).isEmpty();
    }

    private static boolean insideSpawnBuffer(ServerPlayer player, Vec3 center) {
        double dx = center.x - player.getX();
        double dz = center.z - player.getZ();
        return dx * dx + dz * dz < MIN_PLAYER_SPAWN_DISTANCE * MIN_PLAYER_SPAWN_DISTANCE;
    }

    public static void onLeaderDeath(WineFoxEntity fox) {
        if (fox.level() instanceof ServerLevel level && fox.isDeathConfirmed())
            WineFoxPursuitData.get(level).release(fox.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeaderJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob member)) return;
        if (member instanceof WineFoxEntity fox && event.loadedFromDisk()) WineFoxDialogue.skipLoadedArrival(fox);
        CompoundTag identity = member.getPersistentData().getCompound(EVENT);
        if (identity.getBoolean("Pending") && identity.hasUUID("Leader")) {
            UUID leader = identity.getUUID("Leader");
            Pending transaction = PENDING.get(leader);
            if (transaction == null || !transaction.members.contains(member)) {
                if (WineFoxPursuitData.get(level).isCommitted(leader) || WineFoxLeaders.get(level).isDead(leader))
                    finishPending(member);
                else {
                    event.setCanceled(true);
                    member.discard(); // Interrupted preparation, never a death/reward/leader-death notification.
                    return;
                }
            }
        }
        if (!(member instanceof WineFoxEntity fox) || identity.getBoolean("Pending")) return;
        if (fox.isDeathConfirmed() || WineFoxLeaders.get(level).isDead(fox.getUUID())) {
            WineFoxPursuitData.get(level).release(fox.getUUID());
            return;
        }
        if (identity.hasUUID("Player") && identity.hasUUID("Leader") && identity.contains("Respawn")
                && fox.getUUID().equals(identity.getUUID("Leader"))
                && net.minecraft.resources.ResourceLocation.tryParse(identity.getString("Dimension")) != null) {
            WineFoxPursuitData.get(level).record(fox.getUUID(), identity.getString("Dimension"),
                    BlockPos.of(identity.getLong("Respawn")), identity.getUUID("Player"));
            identity.putBoolean("RaidGlowing", true);
            fox.setGlowingTag(true);
        }
    }
    private WineFoxPursuit() {}
}
