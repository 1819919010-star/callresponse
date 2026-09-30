package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.mixin.accessor.MobTargetSelectorAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Adds a target candidate to existing hostile attack AI; only marked camp raids receive extra reach/advance. */
public final class OutpostRaidManager {
    private static final ThreadLocal<BlockPos> CREATION_CENTER = new ThreadLocal<>();

    public static BlockPos raidCenterForCreation(BlockPos vanillaCenter) {
        BlockPos outpost = CREATION_CENTER.get();
        return outpost == null ? vanillaCenter : outpost;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || player.isSpectator()
                || !player.hasEffect(MobEffects.BAD_OMEN)) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD || level.getDifficulty() == Difficulty.PEACEFUL
                || !level.dimensionType().hasRaids()
                || level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) return;

        BetrayalOutpostSavedData.Outpost outpost = BetrayalOutpostSavedData.get(level)
                .findAt(level, player.blockPosition());
        if (outpost == null || level.getRaidAt(outpost.center()) != null) return;

        // 原版负责 Bad Omen、波次、难度、刷怪位置、进度和奖励。这里只给本次创建指定中心。
        CREATION_CENTER.set(outpost.center());
        try {
            Raid raid = level.getRaids().createOrExtendRaid(player);
            if (raid != null && raid.getCenter().equals(outpost.center())) {
                ((OutpostRaidMarker) raid).callresponse$setOutpostRaid(true);
                level.getRaids().setDirty();
            }
        } finally {
            CREATION_CENTER.remove();
        }
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
    public void onRaiderTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Raider raider) || raider.level().isClientSide
                || !raider.isAlive() || !OutpostMaidTargetGoal.isCampRaid(raider)) return;
        LivingEntity current = raider.getTarget();
        if (current instanceof Player player && (player.isCreative() || player.isSpectator())) {
            raider.setTarget(null);
        }
    }

    @SubscribeEvent
    public void onRaiderChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Raider raider)
                || !OutpostMaidTargetGoal.isCampRaid(raider) || event.getNewTarget() == null) return;
        if (event.getNewTarget() instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            event.setNewTarget(null);
        }
    }
}
