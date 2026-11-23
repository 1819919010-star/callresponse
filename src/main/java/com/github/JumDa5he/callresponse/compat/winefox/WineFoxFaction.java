package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostAlertManager;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import javax.annotation.Nullable;
import java.util.UUID;

/** The one relation gate for this faction; it does not replace ordinary camp relations. */
@EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class WineFoxFaction {
    private static final String BINDING = "CallResponseWineFoxBinding";
    public static final double RECRUIT_RANGE = 10;

    public static boolean isRevenge(Entity entity) {
        return entity instanceof EntityMaid maid && BetrayalOutpostMaidData.isOutpostMaid(maid);
    }

    @Nullable public static UUID leaderOf(EntityMaid maid) {
        CompoundTag binding = maid.getPersistentData().getCompound(BINDING);
        if (!binding.hasUUID("Leader")) return null;
        UUID leader = binding.getUUID("Leader");
        if (maid.level() instanceof ServerLevel level && WineFoxLeaders.get(level).isDead(leader)) {
            if (maid.isAlive() && isRevenge(maid)) {
                maid.getPersistentData().remove(BINDING);
                WineFoxSurrender.surrender(maid);
            }
            // A maid can be at zero health inside a cancellable death callback while its leader
            // dies. Retain the receipt link until it survives that callback, rather than losing it.
            return null;
        }
        return leader;
    }

    public static boolean isRecruited(EntityMaid maid) {
        return isRevenge(maid) && leaderOf(maid) != null;
    }

    /** Directional: all revenge maids spare foxes; foxes spare recruited revenge maids. */
    public static boolean blocksAttack(@Nullable Entity attacker, @Nullable Entity target) {
        if (attacker == null || target == null) return false;
        if (attacker instanceof EntityMaid maid && WineFoxSurrender.isSurrendered(maid)) return true;
        if (attacker instanceof WineFoxEntity) {
            return target instanceof WineFoxEntity || target instanceof EntityMaid maid && isRecruited(maid);
        }
        if (isRevenge(attacker)) {
            return target instanceof WineFoxEntity || target instanceof EntityMaid maid
                    && isRecruited((EntityMaid) attacker) && isRecruited(maid);
        }
        return false;
    }

    @Nullable public static Entity resolveAttacker(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) attacker = source.getDirectEntity();
        // Vanilla projectiles retain the actual owner; never infer a nearby player.
        for (int i = 0; i < 4 && attacker instanceof Projectile projectile; i++) {
            Entity owner = projectile.getOwner();
            if (owner == null || owner == attacker) break;
            attacker = owner;
        }
        return attacker;
    }

    public static int recruitNearby(WineFoxEntity fox) {
        if (!(fox.level() instanceof ServerLevel level) || !fox.isAlive() || fox.isRemoved()
                || WineFoxLeaders.get(level).isDead(fox.getUUID())) return 0;
        int recruited = 0;
        for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class,
                fox.getBoundingBox().inflate(RECRUIT_RANGE),
                m -> m.isAlive() && !m.isRemoved() && isRevenge(m)
                        && fox.distanceToSqr(m) <= RECRUIT_RANGE * RECRUIT_RANGE)) {
            if (leaderOf(maid) == null && bind(fox, maid)) recruited++;
        }
        if (recruited > 0) {
            // Clear both sides in the loaded combat neighbourhood immediately. Distant actors
            // also recheck the same gate before targeting or resolving an in-flight attack.
            for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class, fox.getBoundingBox().inflate(64),
                    WineFoxFaction::isRevenge)) BetrayalOutpostAlertManager.clearFriendlyTargets(maid);
            for (WineFoxEntity other : level.getEntitiesOfClass(WineFoxEntity.class, fox.getBoundingBox().inflate(64)))
                other.releaseFriendlyTarget();
        }
        return recruited;
    }

    /** Server-thread compare-before-write, also used by the transactional pursuit spawner. */
    public static boolean bind(WineFoxEntity fox, EntityMaid maid) {
        if (!(fox.level() instanceof ServerLevel level) || maid.level() != level
                || !fox.isAlive() || fox.isRemoved() || !maid.isAlive() || maid.isRemoved()
                || !isRevenge(maid) || WineFoxLeaders.get(level).isDead(fox.getUUID())) return false;
        UUID existing = leaderOf(maid); // May apply a confirmed-death receipt and surrender.
        if (WineFoxSurrender.isSurrendered(maid)) return false;
        if (existing != null) return existing.equals(fox.getUUID());
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Leader", fox.getUUID());
        binding.putString("Dimension", level.dimension().location().toString());
        binding.putLong("Position", fox.blockPosition().asLong());
        maid.getPersistentData().put(BINDING, binding);
        BetrayalOutpostAlertManager.clearFriendlyTargets(maid);
        fox.releaseFriendlyTarget();
        return true;
    }

    /** Called only after the native, cancellable death method has confirmed dead=true. */
    public static void onLeaderDeath(WineFoxEntity fox) {
        if (!(fox.level() instanceof ServerLevel level) || !fox.isDeathConfirmed()) return;
        WineFoxLeaders.get(level).recordDeath(fox.getUUID());
        WineFoxPursuit.onLeaderDeath(fox);
        for (ServerLevel loadedLevel : level.getServer().getAllLevels()) {
            for (Entity entity : loadedLevel.getAllEntities()) {
                if (entity instanceof EntityMaid maid && isRevenge(maid)) leaderOf(maid);
            }
        }
    }

    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof EntityMaid maid && isRevenge(maid))
            leaderOf(maid); // Lazy death receipt also works for an unloaded member across restart/dimensions.
        else if (event.getEntity() instanceof WineFoxEntity fox) {
            if (fox.isDeathConfirmed()) onLeaderDeath(fox);
            else WineFoxGrowth.apply(fox);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBoundMaidTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof EntityMaid maid && !maid.level().isClientSide
                && maid.isAlive() && isRevenge(maid) && maid.getPersistentData().contains(BINDING))
            leaderOf(maid); // Constant-time receipt lookup only; no entity/chunk search.
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingIncomingDamageEvent event) {
        if (blocksAttack(resolveAttacker(event.getSource()), event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (blocksAttack(resolveAttacker(event.getSource()), event.getEntity())) event.setNewDamage(0);
    }

    private WineFoxFaction() {}
}
