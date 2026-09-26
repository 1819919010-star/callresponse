package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Comparator;

/** 只扩展原版 Raid 的营地触发地点，以及本场 Raider 对营地女仆的目标关系。 */
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

    /** 当前营地 Raid 优先交战复仇女仆；没有女仆时仍交还原版目标逻辑。 */
    @SubscribeEvent
    public void onRaiderTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Raider raider) || raider.level().isClientSide
                || !raider.isAlive() || !OutpostMaidTargetGoal.isCampRaid(raider)
                || !(raider.level() instanceof ServerLevel level)) return;
        LivingEntity current = raider.getTarget();
        if (current instanceof Player player && (player.isCreative() || player.isSpectator())) {
            raider.setTarget(null);
            raider.getNavigation().stop();
            current = null;
        }
        if (raider.tickCount % 10 != 0) return;
        if (OutpostMaidTargetGoal.isCampTarget(raider, current)) return;
        EntityMaid nearest = findNearbyCampMaid(level, raider);
        if (nearest == null) return;
        raider.setTarget(nearest);
    }

    @SubscribeEvent
    public void onRaiderChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Raider raider)
                || !(raider.level() instanceof ServerLevel level)
                || !OutpostMaidTargetGoal.isCampRaid(raider) || event.getNewTarget() == null) return;
        EntityMaid maid = findNearbyCampMaid(level, raider);
        if (maid != null) {
            event.setNewTarget(maid);
        } else if (event.getNewTarget() instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            event.setNewTarget(null);
        }
    }

    private static EntityMaid findNearbyCampMaid(ServerLevel level, Raider raider) {
        return level.getEntitiesOfClass(EntityMaid.class,
                        raider.getBoundingBox().inflate(20.0D, 8.0D, 20.0D),
                        maid -> OutpostMaidTargetGoal.isCampTarget(raider, maid))
                .stream().min(Comparator.comparingDouble(raider::distanceToSqr)).orElse(null);
    }
}
