package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public final class WanderingCampData {
    public static final String KEY = "CallResponseWanderingCamp";
    public static final int BASE_ADOPTION_CHANCE = 30;
    private WanderingCampData() {}
    public static CompoundTag data(EntityMaid maid) { return maid.getPersistentData().getCompound(KEY); }
    public static boolean active(EntityMaid maid) {
        return maid.level().isClientSide ? ((CampMaid) maid).callresponse$campView().getBoolean("Active")
                : data(maid).getBoolean("Active");
    }
    public static CompoundTag view(EntityMaid maid) {
        return maid.level().isClientSide ? ((CampMaid) maid).callresponse$campView() : data(maid);
    }
    public static BlockPos head(EntityMaid maid) { return BlockPos.of(view(maid).getLong("Head")); }
    public static BlockPos foot(EntityMaid maid) { return head(maid).relative(facing(maid).getOpposite()); }
    public static boolean sleepingOnBedding(EntityMaid maid) {
        return active(maid) && maid.getSleepingPos().filter(head(maid)::equals).isPresent();
    }
    public static double surfaceY(EntityMaid maid) { return head(maid).getY() + 1.0 / 16; }
    public static Direction facing(EntityMaid maid) {
        return Direction.from2DDataValue(view(maid).getInt("Facing"));
    }
    public static void sync(EntityMaid maid) {
        CompoundTag v = new CompoundTag();
        CompoundTag d = data(maid);
        v.putBoolean("Active", d.getBoolean("Active"));
        v.putBoolean("Considering", d.hasUUID("Inviter"));
        v.putLong("RetryAfter", d.getLong("RetryAfter"));
        v.putString("Camp", d.getString("Camp"));
        v.putLong("Head", d.getLong("Head"));
        v.putInt("Facing", d.getInt("Facing"));
        ((CampMaid) maid).callresponse$campView(v);
    }
    public static int chance(EntityMaid maid, java.util.UUID player) {
        return Math.min(100, BASE_ADOPTION_CHANCE + data(maid).getCompound("Donors").getInt(player.toString()));
    }
    public static void credit(EntityMaid maid, java.util.UUID player, int points) {
        if (player == null || points <= 0) return;
        CompoundTag d = data(maid), donors = d.getCompound("Donors");
        donors.putInt(player.toString(), Math.min(100 - BASE_ADOPTION_CHANCE, donors.getInt(player.toString()) + points));
        d.put("Donors", donors);
    }
}
