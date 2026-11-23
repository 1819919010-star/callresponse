package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import net.minecraft.nbt.NbtOps;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Called only at the verified TLM storage-item release insertion sites. */
public final class MaidReleaseHealth {
    public static final String FILLED = "initial_health_filled";

    /** Film skips Entity.load, the step that normally restores this relationship attachment. */
    public static void readFilmRelationships(EntityMaid maid, CompoundTag tag) {
        if (!(maid.level() instanceof ServerLevel)) return;
        var saved = tag.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(CallResponseMod.MOD_ID + ":emotion");
        if (saved == null) return;
        try {
            var ops = maid.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var relationships = EmotionData.MaidEmotion.CODEC.parse(ops, saved).getOrThrow();
            maid.setData(InitAttachTypes.SYNCED_EMOTION, relationships);
        } catch (RuntimeException error) {
            CallResponseMod.LOGGER.warn("Skipping unreadable CallResponse emotions on a maid film", error);
        }
    }

    /** Film uses readAdditionalSaveData rather than Entity.load, so restore this flag explicitly. */
    public static void readMarker(EntityMaid maid, CompoundTag tag) {
        maid.getPersistentData().putBoolean(FILLED, tag.getCompound("NeoForgeData").getBoolean(FILLED));
    }

    public static boolean addReleased(Level level, Entity entity) {
        return addReleased(level, entity, () -> level.addFreshEntity(entity));
    }

    public static boolean addReleased(Level level, Entity entity, java.util.function.BooleanSupplier insert) {
        if (!(level instanceof ServerLevel) || !(entity instanceof EntityMaid maid)
                || !maid.isAlive() || maid.isRemoved() || !WineFoxPotions.isOrdinaryMaid(maid)
                || maid.getPersistentData().getBoolean(FILLED)) return insert.getAsBoolean();
        float before = maid.getHealth();
        boolean committed = false;
        try {
            boolean added = insert.getAsBoolean();
            if (added && maid.isAlive() && !maid.isRemoved()) {
                // The join has finished. Read the current owner again before the one-time fill.
                EmotionAttributeManager.refresh(maid);
                maid.setHealth(maid.getMaxHealth());
                maid.getPersistentData().putBoolean(FILLED, true);
                committed = true;
            }
            return committed;
        } finally {
            // No marker is written until the real insertion succeeds. This also handles a
            // throwing/canceled join without letting a reused temporary object keep free healing.
            if (!committed && maid.isAlive() && !maid.isRemoved()) maid.setHealth(Math.min(before, maid.getMaxHealth()));
        }
    }

    private MaidReleaseHealth() {}
}
