package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;

/** Adds Revenge Maids as a candidate to mobs that already have their own attack AI. */
public final class OutpostMaidTargetGoal extends NearestAttackableTargetGoal<EntityMaid> {
    private static final double RAID_HORIZONTAL_RANGE = 96.0D;
    private static final double RAID_VERTICAL_RANGE = 24.0D;
    private final Mob hunter;
    private int nextSearchTick;

    public OutpostMaidTargetGoal(Mob hunter) {
        super(hunter, EntityMaid.class, 0, true, false, living -> isAllowedTarget(hunter, living));
        this.hunter = hunter;
        this.nextSearchTick = hunter.getId() % 20;
    }

    @Override
    public boolean canUse() {
        if (hunter.tickCount < nextSearchTick) return false;
        nextSearchTick = hunter.tickCount + 20;
        if (hunter.isNoAi() || IntimidationManager.isIntimidated(hunter)
                || hasValidCombatTarget(hunter)
                || hunter instanceof Raider raider && raider.getCurrentRaid() != null && !isCampRaid(raider))
            return false;
        // The special raid uses its own detection distance; ordinary mobs retain the vanilla attribute.
        this.targetConditions.range(getFollowDistance());
        return super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return !hunter.isNoAi() && !IntimidationManager.isIntimidated(hunter)
                && isAllowedTarget(hunter, hunter.getTarget()) && super.canContinueToUse();
    }

    @Override
    protected double getFollowDistance() {
        return this.mob instanceof Raider raider && isCampRaid(raider)
                ? 100.0D : super.getFollowDistance();
    }

    @Override
    protected AABB getTargetSearchArea(double distance) {
        return this.mob instanceof Raider raider && isCampRaid(raider)
                ? raider.getBoundingBox().inflate(RAID_HORIZONTAL_RANGE, RAID_VERTICAL_RANGE, RAID_HORIZONTAL_RANGE)
                : super.getTargetSearchArea(distance);
    }

    static boolean hasValidCombatTarget(Mob mob) {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive() && !target.isRemoved()
                && (!(target instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
    }

    private static boolean isAllowedTarget(Mob hunter, LivingEntity living) {
        if (!(living instanceof EntityMaid maid) || !maid.isAlive()
                || !BetrayalOutpostMaidData.isOutpostMaid(maid)) return false;
        if (hunter instanceof Raider raider && raider.getCurrentRaid() != null) {
            return isCampTarget(raider, maid);
        }
        return true;
    }

    static boolean isCampRaid(Raider raider) {
        Raid raid = raider.getCurrentRaid();
        return raid != null && raid.isActive() && !raid.isOver()
                && ((OutpostRaidMarker) raid).callresponse$isOutpostRaid();
    }

    static boolean isCampTarget(Raider raider, LivingEntity living) {
        if (!(living instanceof EntityMaid maid) || !maid.isAlive()
                || !BetrayalOutpostMaidData.isOutpostMaid(maid) || !isCampRaid(raider)
                || !(raider.level() instanceof ServerLevel level)) return false;
        BetrayalOutpostSavedData.Outpost outpost = BetrayalOutpostSavedData.get(level)
                .findAt(level, raider.getCurrentRaid().getCenter());
        if (outpost == null || !outpost.key().equals(BetrayalOutpostMaidData.group(maid))) return false;
        double dx = raider.getX() - maid.getX();
        double dz = raider.getZ() - maid.getZ();
        return dx * dx + dz * dz <= RAID_HORIZONTAL_RANGE * RAID_HORIZONTAL_RANGE
                && Math.abs(raider.getY() - maid.getY()) <= RAID_VERTICAL_RANGE;
    }
}
