package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;

/** One placement/validation adapter shared by the server and tracked client profile. */
public final class CampSleepPlacement {
    private CampSleepPlacement() {}
    public static boolean beddingValid(EntityMaid maid) {
        return CampRoutineProfile.bound(maid).filter(p ->
                maid.level().getBlockState(p.head()).is(Blocks.YELLOW_CARPET)
                && maid.level().getBlockState(p.foot()).is(Blocks.YELLOW_CARPET)
                && WanderingCampBrain.safeStand(maid, p.head()) && WanderingCampBrain.safeStand(maid, p.foot())).isPresent();
    }
    public static boolean eligible(EntityMaid maid) {
        return CampRoutineProfile.bound(maid).filter(p -> p.night(maid.level().getDayTime())).isPresent()
                && maid.isAlive() && !WanderingCampBrain.blocked(maid)
                && maid.level().getGameTime() >= WanderingCampData.data(maid).getLong("PanicUntil")
                && (!maid.isSleeping() || maid.position().distanceToSqr(new Vec3(
                        WanderingCampData.head(maid).getX()+.5, WanderingCampData.surfaceY(maid),
                        WanderingCampData.head(maid).getZ()+.5)) < 4)
                && beddingValid(maid);
    }
    public static boolean permitsStart(EntityMaid maid, BlockPos pos) {
        if (!pos.equals(WanderingCampData.head(maid)) || !eligible(maid)) return false;
        // NeoForge has a boolean event, not Forge's tri-state location result. A valid registered
        // surface starts as allowed; every subscriber can still explicitly reject it.
        return NeoForge.EVENT_BUS.post(new CanContinueSleepingEvent(maid, null)).mayContinueSleeping();
    }
    public static void checkLocation(CanContinueSleepingEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && CampRoutineProfile.bound(maid).isPresent()
                && !eligible(maid)) event.setContinueSleeping(false);
        // Never overwrite another subscriber's false with true.
    }
    public static boolean place(EntityMaid maid, BlockPos pos) {
        var profile = CampRoutineProfile.bound(maid).filter(p -> p.head().equals(pos));
        if (profile.isEmpty()) return false;
        var p = profile.get();
        maid.setPos(p.head().getX()+.5, p.surfaceY(), p.head().getZ()+.5);
        return true;
    }
    public static boolean maintain(EntityMaid maid) {
        if (!WanderingCampData.sleepingOnBedding(maid) || CampRoutineProfile.bound(maid).isEmpty()) return false;
        maid.setDeltaMovement(Vec3.ZERO);
        if (!maid.isSilent()) maid.setSilent(true);
        return true;
    }
}
