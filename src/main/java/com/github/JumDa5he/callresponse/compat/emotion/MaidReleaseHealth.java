package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Called only at the verified TLM storage-item release insertion sites. */
public final class MaidReleaseHealth {
    public static final String FILLED = "initial_health_filled";

    /** Entity.load aliases ForgeData; a private copy keeps a failed release from editing the item. */
    public static CompoundTag copyForLoad(CompoundTag data) { return data.copy(); }

    /** Film uses readAdditionalSaveData rather than Entity.load, so restore this flag explicitly. */
    public static void readMarker(EntityMaid maid, CompoundTag tag) {
        maid.getPersistentData().putBoolean(FILLED, tag.getCompound("ForgeData").getBoolean(FILLED));
    }

    public static boolean addReleased(Level level, Entity entity) {
        if (!(level instanceof ServerLevel) || !(entity instanceof EntityMaid maid)
                || !maid.isAlive() || maid.isRemoved() || !WineFoxPotions.isOrdinaryMaid(maid)
                || maid.getPersistentData().getBoolean(FILLED)) return level.addFreshEntity(entity);
        float before = maid.getHealth();
        boolean committed = false;
        try {
            EmotionAttributeManager.refresh(maid);
            maid.setHealth(maid.getMaxHealth());
            boolean added = level.addFreshEntity(maid);
            if (added && maid.isAlive() && !maid.isRemoved()) {
                maid.getPersistentData().putBoolean(FILLED, true);
                committed = true;
            }
            return added;
        } finally {
            // No marker is written until the real insertion succeeds. This also handles a
            // throwing/canceled join without letting a reused temporary object keep free healing.
            if (!committed && maid.isAlive() && !maid.isRemoved()) maid.setHealth(Math.min(before, maid.getMaxHealth()));
        }
    }

    private MaidReleaseHealth() {}
}
