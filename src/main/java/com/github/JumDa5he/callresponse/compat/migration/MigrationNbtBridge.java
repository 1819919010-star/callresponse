package com.github.JumDa5he.callresponse.compat.migration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.UUID;

/** Converts only callresponse's existing NBT on MFM-owned copies; no entity or inventory mutations. */
public final class MigrationNbtBridge {
    private static final String ATTACHMENTS = "neoforge:attachments";
    private static final String[] CONTEXT = {"IsBetraying", "CallResponseBetrayalFormerOwner",
            "forgetTimer", "forgetCountdown", "forgetTriggered", "CallResponseDotingCalmTicks"};
    private static final String[] REVIVE = {"LastDeathType", "ReviveEventPending", "LastDeathGameTime",
            "ReviveEventReadyGameTime", "ReviveDefaultsReset"};
    private static final String[] RUNTIME = {"callresponse:movement_control", "CallResponseNpcEvents",
            "LazyEscapeTick", "DevotedSacrifice", "DevotedSacrificeTick", "CallResponseBetrayalDeathSettled"};
    private MigrationNbtBridge() {}

    public static CompoundTag exportCopy(CompoundTag original) {
        CompoundTag out = original.copy();
        CompoundTag sync = out.getCompound("CallResponseSyncData");
        CompoundTag attachments = out.getCompound(ATTACHMENTS).copy();
        CompoundTag relations = sync.getCompound("Emotions");
        // Old Forge saves may still use EmotionData's existing lazy-read fallback.
        if (relations.isEmpty() && out.getCompound("ForgeData").contains("MaidEmotions", Tag.TAG_COMPOUND))
            relations = out.getCompound("ForgeData").getCompound("MaidEmotions");
        if (sync.contains("Emotions", Tag.TAG_COMPOUND) || !relations.isEmpty()) {
            CompoundTag emotion = new CompoundTag();
            emotion.put("MaidEmotions", validateEmotions(relations));
            attachments.put("callresponse:emotion", emotion);
        }
        if (sync.contains("Hunger", Tag.TAG_FLOAT)) {
            CompoundTag hunger = new CompoundTag();
            hunger.putFloat("MaidHunger", validHunger(sync.getFloat("Hunger")));
            attachments.put("callresponse:hunger", hunger);
        }
        if (!attachments.isEmpty()) out.put(ATTACHMENTS, attachments);
        copyContext(out, "ForgeData", "NeoForgeData");
        stripRevive(out);
        stripRuntime(out);
        return out;
    }

    /** The input is MFM's independently migrated NBT, immediately before its ordinary maid.load. */
    public static void prepareImport(CompoundTag out) {
        CompoundTag sync = out.getCompound("CallResponseSyncData").copy();
        CompoundTag attachments = out.getCompound(ATTACHMENTS);
        if (!sync.contains("Emotions", Tag.TAG_COMPOUND)
                && attachments.contains("callresponse:emotion", Tag.TAG_COMPOUND)) {
            CompoundTag emotion = attachments.getCompound("callresponse:emotion");
            if (!emotion.contains("MaidEmotions", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Missing callresponse:emotion/MaidEmotions");
            sync.put("Emotions", validateEmotions(emotion.getCompound("MaidEmotions")));
        }
        if (!sync.contains("Hunger", Tag.TAG_FLOAT)
                && attachments.contains("callresponse:hunger", Tag.TAG_COMPOUND)) {
            var hunger = attachments.getCompound("callresponse:hunger");
            if (!hunger.contains("MaidHunger", Tag.TAG_FLOAT))
                throw new IllegalArgumentException("Missing callresponse:hunger/MaidHunger");
            sync.putFloat("Hunger", validHunger(hunger.getFloat("MaidHunger")));
        }
        if (!sync.isEmpty()) {
            // Both supported versions define the same default hunger; emotions alone must not load as 0 hunger.
            if (!sync.contains("Hunger", Tag.TAG_FLOAT)) sync.putFloat("Hunger", 50.0F);
            out.put("CallResponseSyncData", sync);
        }
        copyContext(out, "NeoForgeData", "ForgeData");
        stripRevive(out);
        stripRuntime(out);
    }

    private static CompoundTag validateEmotions(CompoundTag map) {
        for (String player : map.getAllKeys()) {
            UUID.fromString(player); // Player relations remain keyed by player UUID, never maid UUID.
            if (!map.contains(player, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Emotion relation " + player);
            var values = map.getCompound(player);
            for (String key : new String[]{"trust", "fear"}) {
                if (!values.contains(key, Tag.TAG_INT) || values.getInt(key) < 0 || values.getInt(key) > 100)
                    throw new IllegalArgumentException("Emotion relation " + player + "/" + key);
            }
        }
        return map.copy();
    }
    private static float validHunger(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException("callresponse hunger");
        return value;
    }
    private static void copyContext(CompoundTag out, String sourceKey, String targetKey) {
        CompoundTag source = out.getCompound(sourceKey);
        CompoundTag target = out.getCompound(targetKey).copy();
        for (String key : CONTEXT) if (source.contains(key)) target.put(key, source.get(key).copy());
        if (!target.isEmpty()) out.put(targetKey, target);
    }
    private static void stripRevive(CompoundTag out) {
        stripRoot(out);
        for (String parent : new String[]{"ForgeData", "NeoForgeData"}) {
            if (!out.contains(parent, Tag.TAG_COMPOUND)) continue;
            CompoundTag data = out.getCompound(parent).copy();
            stripRoot(data);
            out.put(parent, data);
        }
    }
    private static void stripRoot(CompoundTag parent) {
        if (!parent.contains("CallResponseData", Tag.TAG_COMPOUND)) return;
        var data = parent.getCompound("CallResponseData").copy();
        for (String key : REVIVE) data.remove(key);
        if (data.isEmpty()) parent.remove("CallResponseData");
        else parent.put("CallResponseData", data);
    }
    private static void stripRuntime(CompoundTag out) {
        for (String parent : new String[]{"ForgeData", "NeoForgeData"}) {
            if (!out.contains(parent, Tag.TAG_COMPOUND)) continue;
            CompoundTag data = out.getCompound(parent).copy();
            for (String key : RUNTIME) data.remove(key);
            out.put(parent, data);
        }
    }
}
