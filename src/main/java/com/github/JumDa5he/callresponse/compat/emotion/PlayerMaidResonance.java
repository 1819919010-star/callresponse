package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.dispatch.DispatchData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class PlayerMaidResonance {
    private static final int RADIUS = 12;
    private static final ResourceLocation TRUST_HEALTH = ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "player_trust_health_bonus");
    private static final ResourceLocation FEAR_ATTACK = ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "player_fear_attack_bonus");
    private static final Set<ServerPlayer> syncedSweetAttack = Collections.newSetFromMap(new WeakHashMap<>());

    private record Maxima(int trust, int fear) {}

    private static Maxima nearby(ServerPlayer player) {
        int trust = 0, fear = 0;
        ServerLevel level = player.serverLevel();
        UUID owner = player.getUUID();
        var maids = level.getEntitiesOfClass(EntityMaid.class, player.getBoundingBox().inflate(RADIUS),
                maid -> maid.isAlive() && !maid.isRemoved() && owner.equals(maid.getOwnerUUID())
                        && maid.distanceToSqr(player) <= RADIUS * RADIUS && WineFoxPotions.isOrdinaryMaid(maid)
                        && !EmotionBetrayalManager.isBetraying(maid)
                        && !MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE));
        if (!maids.isEmpty()) {
            var dispatched = DispatchData.get(level.getServer()).active(owner);
            for (var maid : maids) {
                if (dispatched.stream().anyMatch(record -> record.maidId().equals(maid.getUUID()))) continue;
                var values = EmotionData.get(maid, owner);
                trust = Math.max(trust, Math.clamp(values.trust(), 0, 100));
                fear = Math.max(fear, Math.clamp(values.fear(), 0, 100));
                if (trust == 100 && fear == 100) break;
            }
        }
        return new Maxima(trust, fear);
    }

    static void relationshipChanged(EntityMaid maid, UUID owner) {
        if (!(maid.level() instanceof ServerLevel level) || owner == null) return;
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> relationshipChanged(maid, owner));
            return;
        }
        if (maid.isAddedToLevel() && owner.equals(maid.getOwnerUUID())
                && level.getPlayerByUUID(owner) instanceof ServerPlayer player
                && maid.distanceToSqr(player) <= RADIUS * RADIUS) refresh(player, false);
    }

    private static void refresh(ServerPlayer player, boolean newClientState) {
        if (!player.isAlive() || player.isRemoved()) return;
        var maxima = nearby(player);
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        // Vanilla restores attributes before saved Health. Keep the last cap across that read.
        if (update(health, TRUST_HEALTH, maxima.trust() * 0.2, true) && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        var attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        boolean changed = update(attack, FEAR_ATTACK, maxima.fear() * 0.1, false);
        if (attack == null || attack.getAttribute().value().isClientSyncable() || player.connection == null) return;
        // Vanilla does not track ATTACK_DAMAGE. Send its current modifiers only to the owner.
        boolean sweet = attack.hasModifier(WineFoxPotions.SWEET_ATTACK_ID);
        boolean sweetChanged = sweet ? syncedSweetAttack.add(player) : syncedSweetAttack.remove(player);
        if (changed || sweetChanged || (newClientState && (sweet || maxima.fear() > 0))) {
            player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), List.of(attack)));
        }
    }

    private static boolean update(AttributeInstance attribute, ResourceLocation id, double amount, boolean save) {
        if (attribute == null) return false;
        var previous = attribute.getModifier(id);
        if (previous != null && previous.amount() == amount && previous.operation() == AttributeModifier.Operation.ADD_VALUE) return false;
        if (previous != null) attribute.removeModifier(id);
        if (amount == 0) return previous != null;
        var modifier = new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE);
        if (save) attribute.addPermanentModifier(modifier);
        else attribute.addTransientModifier(modifier);
        return true;
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && Math.floorMod(player.tickCount + player.getId(), 10) == 0) {
            refresh(player, false);
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player, true);
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player, true);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) refresh(player, true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void reduceDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        var source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || source.is(DamageTypeTags.BYPASSES_RESISTANCE)) return;
        int trust = nearby(player).trust();
        if (trust > 0) event.setNewDamage(event.getNewDamage() * (1.0F - trust / 500.0F));
    }

    private PlayerMaidResonance() {}
}
