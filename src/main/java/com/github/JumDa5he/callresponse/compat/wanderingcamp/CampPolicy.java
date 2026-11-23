package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/** Stage policy only; does not authorize or block the navigation/Brain engine. */
public final class CampPolicy {
    private CampPolicy() {}
    public static boolean active(EntityMaid maid) { return WanderingCampData.active(maid); }
    public static boolean nonCombat(EntityMaid maid) { return !maid.level().isClientSide && active(maid); }
    public static boolean blocksExternalDialogue(EntityMaid maid) { return active(maid); }
    public static boolean blocksDomesticDecision(EntityMaid maid) {
        return nonCombat(maid) && !((CampMaid)maid).callresponse$campTaming();
    }
    public static boolean runsRoutine(EntityMaid maid) {
        return nonCombat(maid) && maid.isAlive() && !maid.isTame() && maid.getOwnerUUID() == null
                && WanderingBehaviorAuthority.bound(maid.getBrain(), maid) && CampRoutineProfile.bound(maid).isPresent();
    }
}
