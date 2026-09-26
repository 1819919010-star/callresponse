package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;

/** 原版 Raider 目标选择器中的营地专用候选，野外 Raider 永远无法启用。 */
public final class OutpostMaidTargetGoal extends NearestAttackableTargetGoal<EntityMaid> {
    private final Raider raider;

    public OutpostMaidTargetGoal(Raider raider) {
        super(raider, EntityMaid.class, 10, false, false, living -> isCampTarget(raider, living));
        this.raider = raider;
        this.targetConditions.ignoreLineOfSight();
    }

    @Override
    public boolean canUse() {
        return isCampRaid(raider) && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return isCampRaid(raider) && isCampTarget(raider, raider.getTarget()) && super.canContinueToUse();
    }

    @Override
    protected AABB getTargetSearchArea(double distance) {
        return raider.getBoundingBox().inflate(Math.min(distance, 20.0D), 8.0D, Math.min(distance, 20.0D));
    }

    static boolean isCampRaid(Raider raider) {
        Raid raid = raider.getCurrentRaid();
        return raid != null && raid.isActive() && !raid.isOver()
                && ((OutpostRaidMarker) raid).callresponse$isOutpostRaid();
    }

    static boolean isCampTarget(Raider raider, LivingEntity living) {
        if (!(living instanceof EntityMaid maid) || !maid.isAlive()
                || !BetrayalOutpostMaidData.isOutpostMaid(maid) || !isCampRaid(raider)) return false;
        Raid raid = raider.getCurrentRaid();
        return raid.getCenter().distSqr(BetrayalOutpostMaidData.home(maid)) <= 32.0D * 32.0D;
    }
}
