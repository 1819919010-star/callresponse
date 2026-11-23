package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.dispatch.DispatchData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEffects;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.MapMaker;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class PlayerMaidResonance {
    private static final int RADIUS = 12;
    private static final int REFRESH_TICKS = 10;
    private static final double HEALTH_PER_TRUST = 0.2;
    private static final double REDUCTION_PER_TRUST = 0.002;
    private static final double ATTACK_PER_FEAR = 0.1;
    private static final UUID TRUST_HEALTH_ID = UUID.fromString("f13923a8-b361-4d95-88e2-0985f6c3a024");
    private static final UUID FEAR_ATTACK_ID = UUID.fromString("419d53bd-a3de-45a3-bfa1-4f326a0f4410");
    private static final Map<ServerPlayer, PlayerState> STATES = new MapMaker().weakKeys().makeMap();

    private static final class PlayerState {
        boolean dirty;
        long arrivalDeadline = -1;
        int sentFear;
        boolean sentSweetness;
        double sentAttack;
    }

    private PlayerMaidResonance() {}

    private static EmotionData.EmotionValues nearby(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        UUID owner = player.getUUID();
        var maids = level.getEntitiesOfClass(EntityMaid.class, player.getBoundingBox().inflate(RADIUS),
                maid -> maid.isAlive() && !maid.isRemoved() && maid.isAddedToWorld()
                        && owner.equals(maid.getOwnerUUID()) && player.distanceToSqr(maid) <= RADIUS * RADIUS
                        && WineFoxPotions.isOrdinaryMaid(maid)
                        && !MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE));
        int trust = 0;
        int fear = 0;
        if (!maids.isEmpty()) {
            var dispatched = DispatchData.get(level.getServer()).active(owner);
            for (EntityMaid maid : maids) {
                if (dispatched.stream().anyMatch(record -> maid.getUUID().equals(record.maidId()))) continue;
                var values = EmotionData.get(maid, owner);
                trust = Math.max(trust, values.trust());
                fear = Math.max(fear, values.fear());
                if (trust >= 100 && fear >= 100) break;
            }
        }
        return new EmotionData.EmotionValues(Math.min(trust, 100), Math.min(fear, 100));
    }

    // Emotion writes and observed ownership changes may arrive together for several maids.
    static void markOwner(EntityMaid maid, UUID owner) {
        if (maid.level() instanceof ServerLevel level && level.getEntity(owner) instanceof ServerPlayer player
                && player.distanceToSqr(maid) <= RADIUS * RADIUS) state(player).dirty = true;
    }

    private static PlayerState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player, ignored -> new PlayerState());
    }

    private static void refresh(ServerPlayer player, PlayerState state, boolean arrival) {
        state.dirty = false;
        if (!player.isAlive() || player.isRemoved()) return;
        var values = nearby(player);
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            boolean hadFear = attack.getModifier(FEAR_ATTACK_ID) != null;
            EmotionAttributeManager.updateAdditiveModifier(attack, FEAR_ATTACK_ID, "callresponse.FearAttackBonus",
                    values.fear() * ATTACK_PER_FEAR);
            syncAttack(player, attack, values.fear(), state, arrival, hadFear && values.fear() == 0);
        }
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        AttributeModifier previous = health.getModifier(TRUST_HEALTH_ID);
        double amount = values.trust() * HEALTH_PER_TRUST;
        boolean lowering = previous != null && previous.getAmount() > amount;
        // The player can arrive before nearby chunk entities finish loading.
        // Briefly keep the saved ceiling so a partial first scan cannot destroy saved hit points.
        if (state.arrivalDeadline >= 0) {
            if (lowering && player.serverLevel().getGameTime() < state.arrivalDeadline && !entityChunksReady(player)) return;
            state.arrivalDeadline = -1;
        }
        EmotionAttributeManager.updateAdditiveModifier(health, TRUST_HEALTH_ID, "callresponse.TrustHealthBonus", amount);
        if (lowering && player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void syncAttack(ServerPlayer player, AttributeInstance attack, int fear, PlayerState state,
                                   boolean arrival, boolean removedFear) {
        // Unlike MAX_HEALTH, vanilla 1.20.1 doesn't track ATTACK_DAMAGE for the client.
        if (player.connection == null || attack.getAttribute().isClientSyncable()) return;
        boolean sweet = player.hasEffect(WineFoxEffects.SWEETNESS.get());
        boolean active = fear > 0 || sweet;
        if (removedFear || state.sentFear != fear || state.sentSweetness != sweet
                || active && (arrival || state.sentAttack != attack.getValue())) {
            player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), List.of(attack)));
            state.sentFear = fear;
            state.sentSweetness = sweet;
            state.sentAttack = attack.getValue();
        }
    }

    private static boolean entityChunksReady(ServerPlayer player) {
        int minX = Mth.floor(player.getX() - RADIUS) >> 4, maxX = Mth.floor(player.getX() + RADIUS) >> 4;
        int minZ = Mth.floor(player.getZ() - RADIUS) >> 4, maxZ = Mth.floor(player.getZ() + RADIUS) >> 4;
        for (int x = minX; x <= maxX; ++x) {
            for (int z = minZ; z <= maxZ; ++z) {
                if (!player.serverLevel().areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var state = state(player);
        if (state.dirty || (player.tickCount + player.getId()) % REFRESH_TICKS == 0
                || (state.arrivalDeadline >= 0 && player.serverLevel().getGameTime() >= state.arrivalDeadline)) {
            refresh(player, state, false);
        }
    }

    private static void onArrival(ServerPlayer player) {
        var state = state(player);
        state.arrivalDeadline = player.serverLevel().getGameTime() + 20;
        refresh(player, state, true);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) onArrival(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) onArrival(player);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) return;
        // Vanilla copies current health on a living End return before copying our maximum.
        // Carry only our contribution through that copy. The respawn event checks the destination.
        var oldHealth = event.getOriginal().getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier oldBonus = oldHealth == null ? null : oldHealth.getModifier(TRUST_HEALTH_ID);
        if (oldBonus != null && player.getHealth() == player.getMaxHealth()
                && event.getOriginal().getHealth() > player.getMaxHealth()) {
            EmotionAttributeManager.updateAdditiveModifier(player.getAttribute(Attributes.MAX_HEALTH),
                    TRUST_HEALTH_ID, "callresponse.TrustHealthBonus", oldBonus.getAmount());
            player.setHealth(Math.min(event.getOriginal().getHealth(), player.getMaxHealth()));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.isEndConquered()) onArrival(player);
            else refresh(player, state(player), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        var source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || source.is(DamageTypeTags.BYPASSES_RESISTANCE)) return;
        // Read the current radius at impact, including movement since the last attribute refresh.
        int trust = nearby(player).trust();
        if (trust > 0) event.setAmount((float) (event.getAmount() * (1.0 - trust * REDUCTION_PER_TRUST)));
    }
}
