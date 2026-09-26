package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.Comparator;

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

    /** 当前营地 Raid 优先交战复仇女仆；没有女仆时仍交还原版目标逻辑。 */
    @SubscribeEvent
    public void onRaiderTick(EntityTickEvent.Post event) {
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
                || !OutpostMaidTargetGoal.isCampRaid(raider) || event.getNewAboutToBeSetTarget() == null) return;
        EntityMaid maid = findNearbyCampMaid(level, raider);
        if (maid != null) {
            event.setNewAboutToBeSetTarget(maid);
        } else if (event.getNewAboutToBeSetTarget() instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    private static EntityMaid findNearbyCampMaid(ServerLevel level, Raider raider) {
        return level.getEntitiesOfClass(EntityMaid.class,
                        raider.getBoundingBox().inflate(20.0D, 8.0D, 20.0D),
                        maid -> OutpostMaidTargetGoal.isCampTarget(raider, maid))
                .stream().min(Comparator.comparingDouble(raider::distanceToSqr)).orElse(null);
    }
}
