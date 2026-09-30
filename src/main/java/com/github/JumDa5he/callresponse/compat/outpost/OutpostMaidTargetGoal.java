package com.github.JumDa5he.callresponse.compat.outpost;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;

/** 低优先级补充候选；只对特殊营地 Raid 扩大搜索，不改实体 FOLLOW_RANGE。 */
public final class OutpostMaidTargetGoal extends NearestAttackableTargetGoal<EntityMaid> {
    static final double RAID_RANGE = 96.0D;
    static final double RAID_HEIGHT = 24.0D;
    private int nextSearchTick;
    private int unseenTicks;
    private boolean startedInCampRaid;
    private Raid startedRaid;

    public OutpostMaidTargetGoal(Mob mob) {
        super(mob, EntityMaid.class, 0, true, false, OutpostMaidTargetGoal::isRevengeMaid);
        nextSearchTick = mob.tickCount + Math.floorMod(mob.getId(), 20);
    }

    @Override
    public boolean canUse() {
        if (!canAct(mob) || hasValidTarget(mob) || !allowsAddedTarget()
                || mob.tickCount < nextSearchTick) return false;
        nextSearchTick = mob.tickCount + 20;
        boolean campRaid = mob instanceof Raider raider && isCampRaid(raider);
        targetConditions.range(campRaid ? -1.0D : getFollowDistance());
        if (!campRaid) return super.canUse();
        // 范围仅在本 Goal 中使用，原版对玩家等目标的范围不变。
        target = mob.level().getEntitiesOfClass(EntityMaid.class,
                        mob.getBoundingBox().inflate(RAID_RANGE, RAID_HEIGHT, RAID_RANGE),
                        maid -> withinRaidRange(mob, maid) && targetConditions.test(mob, maid))
                .stream().min(java.util.Comparator
                        .comparingInt((EntityMaid maid) -> isCampTarget((Raider) mob, maid) ? 0 : 1)
                        .thenComparingDouble(mob::distanceToSqr)).orElse(null);
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!canAct(mob) || !allowsAddedTarget() || mob.getTarget() != target
                || !isRevengeMaid(target) || !hasValidTarget(mob)) return false;
        if (!startedInCampRaid) return super.canContinueToUse();
        if (!(mob instanceof Raider raider) || !isCampRaid(raider)
                || raider.getCurrentRaid() != startedRaid || !withinRaidRange(mob, target)) return false;
        if (mob.getSensing().hasLineOfSight(target)) unseenTicks = 0;
        else if (++unseenTicks > reducedTickDelay(unseenMemoryTicks)) return false;
        return true;
    }

    @Override
    public void start() {
        startedInCampRaid = mob instanceof Raider raider && isCampRaid(raider);
        startedRaid = startedInCampRaid ? ((Raider) mob).getCurrentRaid() : null;
        unseenTicks = 0;
        super.start();
    }

    @Override
    public void stop() {
        // 不清除反击或其他原版 Goal 后来设置的目标。
        if (mob.getTarget() == target) mob.setTarget(null);
        target = null;
        targetMob = null;
        unseenTicks = 0;
        startedInCampRaid = false;
        startedRaid = null;
    }

    private boolean allowsAddedTarget() {
        return !(mob instanceof Raider raider) || raider.getCurrentRaid() == null || isCampRaid(raider);
    }

    static boolean canAct(Mob mob) {
        return mob.isAlive() && !mob.isNoAi() && !IntimidationManager.isIntimidated(mob);
    }

    static boolean hasValidTarget(Mob mob) {
        LivingEntity current = mob.getTarget();
        return current != null && current.isAlive() && !current.isRemoved()
                && current.level() == mob.level() && mob.canAttack(current) && !mob.isAlliedTo(current)
                && !(current instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    private static boolean isRevengeMaid(LivingEntity living) {
        return living instanceof EntityMaid maid && maid.isAlive()
                && BetrayalOutpostMaidData.isOutpostMaid(maid) && !BetrayalOutpostMaidData.isGly(maid);
    }

    private static boolean withinRaidRange(Mob mob, LivingEntity target) {
        double dx = mob.getX() - target.getX(), dz = mob.getZ() - target.getZ();
        return dx * dx + dz * dz <= RAID_RANGE * RAID_RANGE
                && Math.abs(mob.getY() - target.getY()) <= RAID_HEIGHT;
    }

    static boolean isCampRaid(Raider raider) {
        Raid raid = raider.getCurrentRaid();
        return raid != null && raid.isActive() && !raid.isOver()
                && ((OutpostRaidMarker) raid).callresponse$isOutpostRaid();
    }

    static boolean isCampTarget(Raider raider, LivingEntity living) {
        if (!(living instanceof EntityMaid maid) || !isRevengeMaid(maid) || !isCampRaid(raider)) return false;
        Raid raid = raider.getCurrentRaid();
        if (!(raider.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        BetrayalOutpostSavedData.Outpost camp = BetrayalOutpostSavedData.get(level).findAt(level, raid.getCenter());
        return camp != null && camp.key().equals(BetrayalOutpostMaidData.group(maid));
    }
}
