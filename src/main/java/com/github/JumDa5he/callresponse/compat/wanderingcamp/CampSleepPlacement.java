package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.SleepingLocationCheckEvent;
import net.minecraftforge.eventbus.api.Event;

/** Single placement/validation adapter for the bound carpet, shared by server and tracked client data. */
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
        var check = new SleepingLocationCheckEvent(maid, pos);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(check);
        return check.getResult() == Event.Result.ALLOW;
    }
    public static void checkLocation(SleepingLocationCheckEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !CampPolicy.active(maid)
                || !event.getSleepingLocation().equals(WanderingCampData.head(maid))) return;
        // Never replace a third party's explicit safety denial with ALLOW.
        if (event.getResult() != Event.Result.DENY) event.setResult(eligible(maid) ? Event.Result.ALLOW : Event.Result.DENY);
    }
    public static boolean place(EntityMaid maid, BlockPos pos) {
        var profile = CampRoutineProfile.bound(maid).filter(p -> p.head().equals(pos));
        if (profile.isEmpty()) return false;
        var p = profile.get();
        maid.setPos(p.head().getX() + .5, p.surfaceY(), p.head().getZ() + .5);
        return true;
    }
    public static boolean maintain(EntityMaid maid) {
        if (!WanderingCampData.sleepingOnBedding(maid) || CampRoutineProfile.bound(maid).isEmpty()) return false;
        // Native TLM's bed-height correction is replaced locally, not by a second per-tick position write.
        maid.setDeltaMovement(Vec3.ZERO);
        if (!maid.isSilent()) maid.setSilent(true);
        return true;
    }
}
