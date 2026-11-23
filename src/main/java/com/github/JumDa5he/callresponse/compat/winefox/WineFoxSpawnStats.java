package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaxHealth;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/** One spawn-time statistic snapshot, separate from the existing fallen-follower inheritance. */
@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class WineFoxSpawnStats {
    public static final String DATA = "CallResponseWineFoxSpawnStats";
    public static final UUID HEALTH_BONUS = UUID.fromString("c8f2ea68-2184-471d-879e-e19bd9a95f73");
    private static final ThreadLocal<Source> EGG_SOURCE = new ThreadLocal<>();

    /** Only explicit fresh-spawn entrances call this; a loaded established snapshot is never rerolled. */
    public static boolean initializeSpawn(WineFoxEntity fox, @Nullable ServerPlayer player) {
        if (!(fox.level() instanceof ServerLevel) || !fox.isAlive() || fox.isRemoved() || fox.isDeathConfirmed()) return false;
        CompoundTag prior = fox.getPersistentData().getCompound(DATA);
        if (prior.getBoolean("Initialized") && !prior.getBoolean("LegacyUnattributed")) return false;
        int kills = player == null ? 0 : Math.max(0, player.getStats().getValue(Stats.ENTITY_KILLED.get(EntityMaid.TYPE)));
        var maxHealth = fox.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) return false;
        // Existing inheritance is intentionally outside this ordinary attribute value.
        maxHealth.removeModifier(HEALTH_BONUS);
        double bonus = BetrayalOutpostMaxHealth.computeRevengeHealthBonus(kills, maxHealth.getValue());
        CompoundTag snapshot = new CompoundTag();
        snapshot.putBoolean("Initialized", true);
        if (player != null) snapshot.putUUID("Source", player.getUUID());
        snapshot.putInt("Kills", kills);
        snapshot.putDouble("Bonus", bonus);
        fox.getPersistentData().put(DATA, snapshot);
        restoreModifier(fox, bonus);
        fox.setHealth(fox.getMaxHealth());
        return true;
    }

    /** Avoid AttributeInstance.load retaining an old amount when the same entity is reloaded. */
    public static void beforeLoad(WineFoxEntity fox) {
        var attribute = fox.getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) attribute.removeModifier(HEALTH_BONUS);
    }

    public static void afterLoad(WineFoxEntity fox, CompoundTag tag) {
        CompoundTag saved = tag.getCompound("ForgeData").getCompound(DATA);
        if (saved.getBoolean("Initialized")) {
            CompoundTag snapshot = saved.copy();
            snapshot.putInt("Kills", Math.max(0, saved.getInt("Kills")));
            double bonus = WineFoxGrowth.nonnegative(saved.getDouble("Bonus"));
            snapshot.putDouble("Bonus", bonus);
            fox.getPersistentData().put(DATA, snapshot);
            restoreModifier(fox, bonus);
        } else if (tag.hasUUID("UUID") || tag.contains("Attributes") || tag.contains("Health")) {
            // Old world entities are not fresh spawns. An explicit egg/command entrance may still
            // initialize its newly created object after custom EntityTag deserialization.
            CompoundTag legacy = new CompoundTag();
            legacy.putBoolean("Initialized", true);
            legacy.putBoolean("LegacyUnattributed", true);
            legacy.putInt("Kills", 0);
            legacy.putDouble("Bonus", 0);
            fox.getPersistentData().put(DATA, legacy);
        }
        // Never heal here: vanilla already restored saved attributes and current health.
    }

    private static void restoreModifier(WineFoxEntity fox, double bonus) {
        var attribute = fox.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        var current = attribute.getModifier(HEALTH_BONUS);
        if (current != null && current.getOperation() == AttributeModifier.Operation.ADDITION
                && current.getAmount() == bonus && bonus > 0) return;
        attribute.removeModifier(HEALTH_BONUS);
        if (bonus > 0) attribute.addPermanentModifier(new AttributeModifier(HEALTH_BONUS,
                "callresponse.winefox_spawn_statistics", bonus, AttributeModifier.Operation.ADDITION));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void joining(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof WineFoxEntity fox) || event.getLevel().isClientSide()) return;
        Source source = EGG_SOURCE.get();
        if (source != null) initializeSpawn(fox, source.player());
        else if (!fox.getPersistentData().getCompound(DATA).getBoolean("Initialized")) initializeSpawn(fox, null);
    }

    /** Only the CallResponse egg opens this scope; nested uses and failures restore the prior scope. */
    public static EggScope eggSource(@Nullable Player player) {
        Source old = EGG_SOURCE.get();
        EGG_SOURCE.set(new Source(player instanceof ServerPlayer server ? server : null));
        return new EggScope(old);
    }

    private record Source(@Nullable ServerPlayer player) {}
    public static final class EggScope implements AutoCloseable {
        private final Source prior;
        private EggScope(Source prior) { this.prior = prior; }
        @Override public void close() {
            if (prior == null) EGG_SOURCE.remove(); else EGG_SOURCE.set(prior);
        }
    }
    private WineFoxSpawnStats() {}
}
