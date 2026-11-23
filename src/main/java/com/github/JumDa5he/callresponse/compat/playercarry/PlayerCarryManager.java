package com.github.JumDa5he.callresponse.compat.playercarry;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.task.PrincessCarryTask;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidData;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampData;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Restored real-player passenger branch. Server authority; no player ability changes. */
public final class PlayerCarryManager {
    public static final String STATE = "PLAYER_HELD_BY_MAID";
    private static final Vec3 OFFSET = new Vec3(0, 0.15, 0.75);
    private static final Map<UUID, Session> HELD = new HashMap<>();
    private static final Map<UUID, UUID> HOLDERS = new HashMap<>();
    private static final Map<UUID, View> CLIENT = new HashMap<>();

    public record View(UUID holder, long started, Vec3 offset, boolean ysmAnimation) {}

    private static final class Session {
        final ServerPlayer player;
        final EntityMaid holder;
        final ServerLevel level;
        final long started;
        final boolean priorArms;
        final Vec3 origin, offset = OFFSET;
        boolean releasing, releaseRequested, ysmAnimation;
        Session(ServerPlayer player, EntityMaid holder) {
            this.player = player; this.holder = holder; this.level = player.serverLevel();
            this.started = level.getGameTime(); this.priorArms = holder.isSwingingArms();
            this.origin = player.position();
        }
    }

    private static boolean ready(EntityMaid maid, ServerPlayer player) {
        return maid.isAlive() && !maid.isRemoved() && maid.level() == player.level()
                && maid.isTame() && maid.isOwnedBy(player) && !maid.isSleeping() && !maid.isInSittingPose()
                && !maid.isNoAi() && !maid.isPassenger() && !maid.isLeashed()
                && maid.getTarget() == null && !maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !EmotionBetrayalManager.isBetraying(maid) && !BetrayalOutpostMaidData.isOutpostMaid(maid)
                && !WanderingCampData.active(maid) && !WanderingMaidData.isSpecial(maid)
                && !TradingMaidData.isTrading(maid) && !IntimidationManager.isIntimidated(maid)
                && !TalkEventManager.isParticipant(maid)
                && PrincessCarryTask.isCurrentTask(maid) && PrincessCarryTask.hasSaddle(maid)
                && maid.getPersistentData().getCompound(MaidMovementControl.ROOT_KEY).getCompound("Reasons").isEmpty();
    }

    public static boolean start(ServerPlayer player, EntityMaid maid) {
        if (!player.server.isSameThread() || HELD.containsKey(player.getUUID())
                || HOLDERS.containsKey(maid.getUUID()) || !ready(maid, player)
                || !player.isAlive() || player.hasDisconnected() || player.isSpectator() || player.isSleeping()
                || player.isFallFlying() || player.isPassenger() || !player.getPassengers().isEmpty()
                || !maid.getPassengers().isEmpty() || maid.distanceToSqr(player) > 9
                || !maid.onGround() || !player.onGround()
                || !safePoint(player.serverLevel(), player, player.position())) return false;
        Session session = new Session(player, maid);
        HELD.put(player.getUUID(), session);
        HOLDERS.put(maid.getUUID(), player.getUUID());
        boolean mounted = false;
        try {
            mounted = player.startRiding(maid, false) && player.getVehicle() == maid;
        } finally {
            if (!mounted) {
                HELD.remove(player.getUUID(), session);
                HOLDERS.remove(maid.getUUID(), player.getUUID());
            }
        }
        if (!mounted) return false;
        com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager.cancelRequest(maid);
        maid.setSwingingArms(true);
        player.stopUsingItem();
        player.setSprinting(false);
        player.setJumping(false);
        player.xxa = player.zza = 0;
        session.ysmAnimation = playYsm(player, "carryon:princess");
        syncVehicle(session);
        broadcast(player, new PlayerCarrySyncS2CPacket(player.getUUID(), maid.getUUID(), session.started, session.offset, session.ysmAnimation));
        return true;
    }

    public static boolean isHeld(Player player) {
        if (player.level().isClientSide) {
            View view = CLIENT.get(player.getUUID());
            return view != null && player.getVehicle() != null && view.holder.equals(player.getVehicle().getUUID());
        }
        Session session = HELD.get(player.getUUID());
        return session != null && !session.releasing && player.getVehicle() == session.holder;
    }

    public static boolean isHolder(EntityMaid maid) {
        return maid.getFirstPassenger() instanceof Player player && isHeld(player);
    }

    public static boolean nativePlayerAnimation(Player player) {
        View view = CLIENT.get(player.getUUID());
        return isHeld(player) && view != null && view.ysmAnimation;
    }

    /** Use YSM's documented server command API; never load its client renderer on a server. */
    private static boolean playYsm(ServerPlayer player, String animation) {
        String version = net.minecraftforge.fml.ModList.get().getModContainerById("yes_steve_model")
                .map(container -> container.getModInfo().getVersion().toString()).orElse("");
        if (!"2.6.5-forge+mc1.20.1".equals(version)) return false;
        var dispatcher = player.server.getCommands().getDispatcher();
        if (dispatcher.getRoot().getChild("ysm") == null) return false;
        try {
            // @s remains the exact source player even during logout; ':' requires Brigadier quoting.
            return dispatcher.execute("ysm play @s " + com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(animation),
                    player.createCommandSourceStack().withPermission(2).withSuppressedOutput()) > 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException | LinkageError failure) {
            CallResponseMod.LOGGER.warn("YSM player carry animation {} unavailable for {}; retaining ordinary player rendering",
                    animation, player.getUUID(), failure);
            return false;
        }
    }

    /** Only this exact registered player/maid relation gets the restored offset. */
    public static Vec3 offset(EntityMaid maid, Player player) {
        if (!isHeld(player) || player.getVehicle() != maid) return null;
        return player.level().isClientSide ? CLIENT.get(player.getUUID()).offset : HELD.get(player.getUUID()).offset;
    }

    public static void acceptClient(PlayerCarrySyncS2CPacket packet) {
        if (packet.holderId() == null) CLIENT.remove(packet.playerId());
        else CLIENT.put(packet.playerId(), new View(packet.holderId(), packet.started(), packet.offset(), packet.ysmAnimation()));
    }
    public static void clearClient() { CLIENT.clear(); }
    public static void forgetClient(UUID player) { CLIENT.remove(player); }

    public static void releaseHolder(EntityMaid maid) {
        UUID player = HOLDERS.get(maid.getUUID());
        Session session = player == null ? null : HELD.get(player);
        if (session != null && session.holder == maid) release(session.player);
    }

    public static boolean release(ServerPlayer player) {
        Session session = HELD.get(player.getUUID());
        if (session == null || session.releasing) return false;
        session.releasing = true;
        try {
            if (player.getVehicle() == session.holder) {
                Vec3 safe = player.level() == session.level && player.isAlive() ? landing(session) : null;
                player.stopRiding();
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0;
                if (safe != null) player.connection.teleport(safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
            }
        } finally {
            if (session.ysmAnimation) playYsm(player, "stop");
            HELD.remove(player.getUUID(), session);
            HOLDERS.remove(session.holder.getUUID(), player.getUUID());
            session.holder.setSwingingArms(session.priorArms);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            player.setJumping(false);
            player.xxa = player.zza = 0;
            syncVehicle(session);
            broadcast(player, new PlayerCarrySyncS2CPacket(player.getUUID(), null, 0, Vec3.ZERO, false));
        }
        return true;
    }

    private static Vec3 landing(Session session) {
        Vec3 origin = session.holder.level() == session.level ? session.holder.position() : session.player.position();
        for (double radius : new double[]{1.25, 2.25, 3.25, 4.25, 0}) {
            for (int direction = 0; direction < 8; direction++) {
                double angle = Math.toRadians(session.holder.getYRot() + direction * 45);
                double x = origin.x - Math.sin(angle) * radius, z = origin.z + Math.cos(angle) * radius;
                for (int dy = 2; dy >= -4; dy--) {
                    BlockPos ground = BlockPos.containing(x, origin.y + dy, z);
                    if (!session.level.hasChunkAt(ground)) continue;
                    var block = session.level.getBlockState(ground);
                    var shape = block.getCollisionShape(session.level, ground);
                    if (shape.isEmpty() || dangerous(block)) continue;
                    Vec3 point = new Vec3(x, ground.getY() + shape.max(Direction.Axis.Y), z);
                    if (safePoint(session.level, session.player, point)) return point;
                }
            }
        }
        // Fall back only to an already loaded, still unobstructed pickup point. Never load new chunks.
        Vec3 point = session.origin;
        return safePoint(session.level, session.player, point) ? point : null;
    }

    private static boolean dangerous(net.minecraft.world.level.block.state.BlockState block) {
        return !block.getFluidState().isEmpty() || block.is(BlockTags.FIRE)
                || block.is(Blocks.MAGMA_BLOCK) || block.is(Blocks.CACTUS) || block.is(Blocks.POWDER_SNOW)
                || block.is(Blocks.SWEET_BERRY_BUSH) || block.is(Blocks.WITHER_ROSE)
                || net.minecraft.world.level.block.CampfireBlock.isLitCampfire(block);
    }

    static boolean safePoint(ServerLevel level, ServerPlayer player, Vec3 point) {
        var box = player.getDimensions(Pose.STANDING).makeBoundingBox(point.x, point.y, point.z);
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(box)) return false;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY - .01, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(pos) || dangerous(level.getBlockState(pos))) return false;
        }
        BlockPos below = BlockPos.containing(point.x, point.y - .01, point.z);
        var support = level.getBlockState(below).getCollisionShape(level, below);
        return !support.isEmpty() && Math.abs(below.getY() + support.max(Direction.Axis.Y) - point.y) < .02
                && level.noCollision(player, box);
    }

    private static void broadcast(ServerPlayer player, PlayerCarrySyncS2CPacket packet) {
        CallResponseMod.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }
    private static void syncVehicle(Session session) {
        var packet = new ClientboundSetPassengersPacket(session.holder);
        session.level.getChunkSource().broadcast(session.holder, packet);
        session.player.connection.send(packet);
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Session session : List.copyOf(HELD.values())) {
            if (session.level.getServer() != event.getServer()) continue;
            if (session.releaseRequested || session.player.isShiftKeyDown() || !ready(session.holder, session.player) || session.holder.getPassengers().size() != 1
                    || !session.player.isAlive() || session.player.hasDisconnected() || session.player.isSpectator()
                    || session.player.isSleeping() || session.player.getVehicle() != session.holder
                    || session.level.getEntity(session.holder.getUUID()) != session.holder
                    || !session.level.noCollision(session.player, session.player.getBoundingBox())
                    || session.player.distanceToSqr(session.holder) > 16) release(session.player);
        }
    }

    /** A cancelled third-party dismount cannot strand this session's explicitly releasing passenger. */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void dismount(EntityMountEvent event) {
        if (event.getEntityMounting() instanceof ServerPlayer player) {
            Session session = HELD.get(player.getUUID());
            if (session == null) return;
            if (event.isMounting() && !session.releasing && event.getEntityBeingMounted() != session.holder) {
                event.setCanceled(true);
                session.releaseRequested = true;
            } else if (event.isDismounting() && event.getEntityBeingMounted() == session.holder) {
                if (session.releasing) event.setCanceled(false);
                // Let the outer vanilla dismount call finish before choosing our safe landing.
                else { event.setCanceled(true); session.releaseRequested = true; }
            }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void hurt(LivingHurtEvent event) {
        if (event.getAmount() > 0 && event.getEntity() instanceof EntityMaid maid) releaseHolder(maid);
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void death(LivingDeathEvent event) { releaseEntity(event.getEntity()); }
    @SubscribeEvent
    public void leave(EntityLeaveLevelEvent event) { if (!event.getLevel().isClientSide) releaseEntity(event.getEntity()); }
    public static void releaseEntity(Entity entity) {
        if (entity instanceof EntityMaid maid) releaseHolder(maid);
        else if (entity instanceof ServerPlayer player) release(player);
    }
    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) { releaseEntity(event.getEntity()); }
    @SubscribeEvent
    public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { releaseEntity(event.getEntity()); }
    @SubscribeEvent
    public void clone(PlayerEvent.Clone event) { releaseEntity(event.getOriginal()); }
    @SubscribeEvent
    public void storage(MaidAndItemTransformEvent.ToItem event) { releaseHolder(event.getMaid()); }
    @SubscribeEvent
    public void stop(ServerStoppingEvent event) {
        for (Session session : List.copyOf(HELD.values())) if (session.level.getServer() == event.getServer()) release(session.player);
    }
    @SubscribeEvent
    public void tracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher)) return;
        for (Session session : HELD.values()) if (event.getTarget() == session.player || event.getTarget() == session.holder) {
            CallResponseMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher), new PlayerCarrySyncS2CPacket(
                    session.player.getUUID(), session.holder.getUUID(), session.started, session.offset, session.ysmAnimation));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void attack(AttackEntityEvent event) { if (isHeld(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.isCancelable() && isHeld(event.getEntity())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void breakBlock(BlockEvent.BreakEvent event) { if (isHeld(event.getPlayer())) event.setCanceled(true); }

}
