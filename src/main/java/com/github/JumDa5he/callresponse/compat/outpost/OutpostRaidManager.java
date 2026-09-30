package com.github.JumDa5he.callresponse.compat.outpost;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;


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

    /** 实体每次加入服务端时幂等补充，不改原版目标 Goal 的优先级。 */
    @SubscribeEvent
    public void onMonsterJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)
                || !(mob instanceof net.minecraft.world.entity.monster.Enemy)
                || mob instanceof net.minecraft.world.entity.NeutralMob) return;
        var selector = ((com.github.JumDa5he.callresponse.mixin.accessor.MobTargetSelectorAccessor) mob)
                .callresponse$getTargetSelector();
        // 没有常规目标 Goal 的 Brain/特殊攻击生物不在本轮改写范围。
        if (selector.getAvailableGoals().stream().noneMatch(goal ->
                goal.getGoal() instanceof net.minecraft.world.entity.ai.goal.target.TargetGoal)
                || selector.getAvailableGoals().stream().anyMatch(goal ->
                goal.getGoal() instanceof OutpostMaidTargetGoal)) return;
        int priority = selector.getAvailableGoals().stream()
                .mapToInt(net.minecraft.world.entity.ai.goal.WrappedGoal::getPriority).max().orElse(0);
        selector.addGoal(priority == Integer.MAX_VALUE ? priority : priority + 1, new OutpostMaidTargetGoal(mob));
    }
}
