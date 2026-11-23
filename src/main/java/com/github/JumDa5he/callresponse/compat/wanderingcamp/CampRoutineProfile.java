package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.List;
import java.util.Optional;

/** Placement and schedule data: full XYZ, never the technical marker. */
public record CampRoutineProfile(String campId, String dimension, BlockPos center, int radius,
                                 BlockPos sit, BlockPos head, BlockPos foot, Direction facing,
                                 Surface surface, List<Integer> sitTimes, int nightStart, int nightEnd) {
    public enum Surface { YELLOW_CARPET }
    public boolean night(long dayTime) {
        long time = Math.floorMod(dayTime, 24000);
        return time >= nightStart && time < nightEnd;
    }
    public double surfaceY() { return head.getY() + 1.0 / 16; }
    public static Optional<CampRoutineProfile> bound(EntityMaid maid) {
        var d = WanderingCampData.view(maid);
        if (!CampPolicy.active(maid) || !d.contains("Head") || !d.contains("Facing")) return Optional.empty();
        String dimension = maid.level().dimension().location().toString();
        String id = d.getString("Camp");
        if (id.isEmpty() || !id.startsWith(dimension + "@")) return Optional.empty();
        BlockPos head = WanderingCampData.head(maid);
        if (head.getY() < maid.level().getMinBuildHeight() || head.getY() >= maid.level().getMaxBuildHeight())
            return Optional.empty();
        Direction facing = WanderingCampData.facing(maid);
        return Optional.of(new CampRoutineProfile(id, dimension, head, WanderingCampManager.RADIUS,
                head, head, head.relative(facing.getOpposite()), facing, Surface.YELLOW_CARPET,
                List.of(WanderingCampManager.SIT_TIMES[0], WanderingCampManager.SIT_TIMES[1]),
                WanderingCampManager.NIGHT_START, WanderingCampManager.NIGHT_END));
    }
}
