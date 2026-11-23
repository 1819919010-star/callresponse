package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.mixin.accessor.MobTargetSelectorAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;


/** 只扩展原版 Raid 的营地触发地点，以及本场 Raider 对营地女仆的目标关系。 */
public final class OutpostRaidManager {
    private static final ThreadLocal<BlockPos> CREATION_CENTER = new ThreadLocal<>();
    private static final String PENDING_OMEN = "CallResponseOutpostRaidOmen";

    public static BlockPos raidCenterForCreation(BlockPos vanillaCenter) {
        BlockPos outpost = CREATION_CENTER.get();
        return outpost == null ? vanillaCenter : outpost;
    }

    /** 1.21 的 Bad Omen 先转为持续 600 tick 的 Raid Omen，再由原版创建 Raid。 */
    public static void beginRaidCreation(ServerPlayer player, BlockPos pos) {
        CompoundTag pending = player.getPersistentData().getCompound(PENDING_OMEN);
        if (!pending.contains("Center") || !pending.getString("Dimension")
                .equals(player.level().dimension().location().toString())) return;
        BlockPos center = BlockPos.of(pending.getLong("Center"));
        if (center.equals(pos) && center.equals(player.getRaidOmenPosition())
                && BetrayalOutpostSavedData.get(player.serverLevel()).findAt(player.serverLevel(), center) != null) {
            CREATION_CENTER.set(center);
        }
    }

    public static void finishRaidCreation(ServerPlayer player, Raid raid) {
        BlockPos center = CREATION_CENTER.get();
        CREATION_CENTER.remove();
        if (center == null) return;
        player.getPersistentData().remove(PENDING_OMEN);
        if (raid != null && raid.getCenter().equals(center)) {
            ((OutpostRaidMarker) raid).callresponse$setOutpostRaid(true);
            player.serverLevel().getRaids().setDirty();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || player.isSpectator()) return;
        if (player.getPersistentData().contains(PENDING_OMEN)) {
            CompoundTag pending = player.getPersistentData().getCompound(PENDING_OMEN);
            if (!player.hasEffect(MobEffects.RAID_OMEN)
                    || !pending.getString("Dimension").equals(player.level().dimension().location().toString())) {
                player.clearRaidOmenPosition();
                player.getPersistentData().remove(PENDING_OMEN);
            }
            return;
        }
        MobEffectInstance badOmen = player.getEffect(MobEffects.BAD_OMEN);
        if (badOmen == null) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.dimensionType().hasRaids()
                || level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) return;

        BetrayalOutpostSavedData.Outpost outpost = BetrayalOutpostSavedData.get(level)
                .findAt(level, player.blockPosition());
        if (outpost == null || level.getRaidAt(outpost.center()) != null) return;

        CompoundTag pending = new CompoundTag();
        pending.putLong("Center", outpost.center().asLong());
        pending.putString("Dimension", level.dimension().location().toString());
        player.getPersistentData().put(PENDING_OMEN, pending);
        player.setRaidOmenPosition(outpost.center());
        player.addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 600, badOmen.getAmplifier()));
        player.removeEffect(MobEffects.BAD_OMEN);
    }

    @SubscribeEvent
    public void onHostileJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Mob mob)
                || !(mob instanceof Enemy) || mob instanceof NeutralMob) return;
        MobTargetSelectorAccessor goals = (MobTargetSelectorAccessor) mob;
        // A target alone cannot teach a special mob how to attack. Raiders already have raid combat AI;
        // other mobs must have a normal melee or ranged attack goal.
        if (!(mob instanceof Raider) && goals.callresponse$getGoalSelector().getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof MeleeAttackGoal
                        || wrapped.getGoal() instanceof RangedAttackGoal
                        || wrapped.getGoal() instanceof RangedBowAttackGoal<?>
                        || wrapped.getGoal() instanceof RangedCrossbowAttackGoal<?>)) return;
        if (goals.callresponse$getTargetSelector().getAvailableGoals().stream()
                .noneMatch(wrapped -> wrapped.getGoal() instanceof OutpostMaidTargetGoal)) {
            goals.callresponse$getTargetSelector().addGoal(6, new OutpostMaidTargetGoal(mob));
        }
    }

    /** A creative/spectator target must not block the camp goal, but valid vanilla targets stay intact. */
    @SubscribeEvent
    public void onRaiderTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Raider raider) || raider.level().isClientSide()
                || !raider.isAlive() || !OutpostMaidTargetGoal.isCampRaid(raider)) return;
        LivingEntity current = raider.getTarget();
        if (current instanceof Player player && (player.isCreative() || player.isSpectator())) {
            raider.setTarget(null);
        }
    }

    @SubscribeEvent
    public void onRaiderChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Raider raider)
                || !OutpostMaidTargetGoal.isCampRaid(raider) || event.getNewAboutToBeSetTarget() == null) return;
        if (event.getNewAboutToBeSetTarget() instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }
}
