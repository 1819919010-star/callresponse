package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative disguise duration, XP payment, owned model snapshots, and recognition effect. */
public final class DisguiseManager {
    public static final int XP_COST = 1000;
    public static final int DISGUISE_TICKS = 20 * 60 * 30;
    public static final int RECOGNITION_TICKS = 20 * 60 * 20;
    private static final String ROOT = "CallResponseDisguise";
    private static final String APPEARANCE = "Appearance";
    private static final String REMAINING = "Remaining";
    private static final String LEGACY_RECOGNITIONS = "Recognitions";
    private static final String LAST_USE_TICK = "LastUseTick";

    public static boolean isActive(ServerPlayer player) {
        CompoundTag state = read(player);
        return state.getInt(REMAINING) > 0 && state.contains(APPEARANCE, Tag.TAG_COMPOUND)
                && DisguiseAppearance.load(state.getCompound(APPEARANCE)).usable();
    }

    /** The visible effect is authoritative; a command-granted effect works without hidden camp NBT. */
    public static boolean isRecognized(ServerPlayer player) {
        return isActive(player) && player.hasEffect(ModDisguiseEffects.OUTPOST_RECOGNITION);
    }

    public static void recognize(ServerPlayer player) {
        // A camp-raid victory grants recognition even before the player applies a disguise.
        // Friendly camp access still requires both the effect and an active disguise.
        MobEffectInstance current = player.getEffect(ModDisguiseEffects.OUTPOST_RECOGNITION);
        player.addEffect(new MobEffectInstance(ModDisguiseEffects.OUTPOST_RECOGNITION,
                Math.max(RECOGNITION_TICKS, current == null ? 0 : current.getDuration()), 0, false, true));
        OutpostDisguiseRelations.clearOldTargetsNear(player);
        sync(player);
    }

    public static void revokeRecognition(ServerPlayer player) {
        CompoundTag state = read(player);
        if (state.contains(LEGACY_RECOGNITIONS)) {
            state.remove(LEGACY_RECOGNITIONS);
            write(player, state);
        }
        if (player.removeEffect(ModDisguiseEffects.OUTPOST_RECOGNITION)) sync(player);
    }

    /** One server-side transaction per game tick; neither hand nor a duplicate packet can charge twice. */
    public static boolean use(ServerPlayer player, boolean sneaking) {
        CompoundTag state = read(player);
        long now = player.level().getGameTime();
        if (state.contains(LAST_USE_TICK, Tag.TAG_LONG) && state.getLong(LAST_USE_TICK) == now) return true;
        state.putLong(LAST_USE_TICK, now);
        write(player, state);
        if (sneaking) {
            boolean wasActive = isActive(player);
            clear(player);
            if (wasActive) player.displayClientMessage(
                    Component.translatable("message.callresponse.disguise.canceled"), true);
            return true;
        }

        DisguiseAppearance appearance;
        if (isActive(player)) {
            appearance = DisguiseAppearance.load(state.getCompound(APPEARANCE));
        } else {
            OwnedMaidAppearanceSavedData registry = OwnedMaidAppearanceSavedData.get(player.server);
            List<DisguiseAppearance> choices = new ArrayList<>();
            for (Map.Entry<UUID, DisguiseAppearance> candidate : registry.choices(player.getUUID()).entrySet()) {
                Entity loaded = null;
                for (ServerLevel loadedLevel : player.server.getAllLevels()) {
                    loaded = loadedLevel.getEntity(candidate.getKey());
                    if (loaded != null) break;
                }
                if (loaded != null) {
                    if (!(loaded instanceof EntityMaid maid) || !maid.isAlive() || !maid.isTame()
                            || !player.getUUID().equals(maid.getOwnerUUID())) {
                        registry.remove(candidate.getKey());
                        continue;
                    }
                    registry.observe(maid);
                    if (DisguiseAppearance.capture(maid).usable()) choices.add(DisguiseAppearance.capture(maid));
                } else if (candidate.getValue().usable()) {
                    choices.add(candidate.getValue());
                }
            }
            if (choices.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.callresponse.disguise.no_maid"), true);
                return false;
            }
            appearance = choices.get(player.getRandom().nextInt(choices.size()));
        }
        if (!payExactExperience(player, XP_COST)) {
            player.displayClientMessage(Component.translatable("message.callresponse.disguise.no_xp", XP_COST), true);
            return false;
        }
        state.put(APPEARANCE, appearance.save());
        state.putInt(REMAINING, DISGUISE_TICKS);
        write(player, state);
        OutpostDisguiseRelations.clearOldTargetsNear(player);
        sync(player);
        player.displayClientMessage(Component.translatable("message.callresponse.disguise.applied"), true);
        return true;
    }

    private static boolean payExactExperience(ServerPlayer player, int cost) {
        long before = availableExperience(player);
        if (before < cost) return false;
        int oldLevel = player.experienceLevel;
        float oldProgress = player.experienceProgress;
        int oldTotal = player.totalExperience;
        player.giveExperiencePoints(-cost);
        if (before - availableExperience(player) == cost) return true;
        // Keep the transaction atomic if another XP implementation refuses or changes the deduction.
        player.experienceLevel = oldLevel;
        player.experienceProgress = oldProgress;
        player.totalExperience = oldTotal;
        player.giveExperiencePoints(0);
        return false;
    }

    private static long availableExperience(Player player) {
        long level = Math.max(0, player.experienceLevel);
        long base = level <= 16 ? level * level + 6 * level
                : level <= 31 ? (5 * level * level - 81 * level + 720) / 2
                : (9 * level * level - 325 * level + 4440) / 2;
        return base + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static CompoundTag read(Player player) {
        return persisted(player).getCompound(ROOT);
    }

    private static void write(Player player, CompoundTag state) {
        CompoundTag persisted = persisted(player);
        persisted.put(ROOT, state);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    public static void clear(ServerPlayer player) {
        CompoundTag persisted = persisted(player);
        persisted.remove(ROOT);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        player.removeEffect(ModDisguiseEffects.OUTPOST_RECOGNITION);
        sync(player);
    }

    private static DisguiseSyncPacket snapshot(ServerPlayer player) {
        CompoundTag state = read(player);
        DisguiseAppearance appearance = isActive(player)
                ? DisguiseAppearance.load(state.getCompound(APPEARANCE)) : null;
        MobEffectInstance recognition = player.getEffect(ModDisguiseEffects.OUTPOST_RECOGNITION);
        return new DisguiseSyncPacket(player.getUUID(), appearance,
                Math.max(0, state.getInt(REMAINING)),
                isActive(player) && recognition != null ? recognition.getDuration() : 0);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, snapshot(player));
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag state = read(player);
        if (state.getInt(REMAINING) <= 0) return;
        int remaining = state.getInt(REMAINING) - 1;
        if (remaining <= 0) {
            clear(player);
            return;
        }
        state.putInt(REMAINING, remaining);
        // Old camp-bound entries are no longer an authorization source or a second duration timer.
        state.remove(LEGACY_RECOGNITIONS);
        write(player, state);
    }

    @SubscribeEvent
    public void onMaidJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && event.getLevel() instanceof ServerLevel level) {
            OwnedMaidAppearanceSavedData.get(level.getServer()).observe(maid);
        }
    }

    @SubscribeEvent
    public void onMaidTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof EntityMaid maid && maid.tickCount % 100 == 0
                && maid.level() instanceof ServerLevel level) {
            OwnedMaidAppearanceSavedData.get(level.getServer()).observe(maid);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
        else if (event.getEntity() instanceof EntityMaid maid && maid.level() instanceof ServerLevel level) {
            OwnedMaidAppearanceSavedData.get(level.getServer()).remove(maid.getUUID());
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() && event.getOriginal().getPersistentData().contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG,
                    event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).copy());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sync(player);
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player && isActive(other)) {
                PacketDistributor.sendToPlayer(player, snapshot(other));
            }
        }
    }

    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target && isActive(target)) {
            PacketDistributor.sendToPlayer(observer, snapshot(target));
        }
    }
}
