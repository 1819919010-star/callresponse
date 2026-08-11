package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionData {
    private static final String EMOTION_TAG = "MaidEmotions";

    // ===== 同步数据 key（通过 SynchedEntityData 自动同步到客户端） =====
    public static final EntityDataAccessor<CompoundTag> EMOTION_KEY =
            SynchedEntityData.defineId(EntityMaid.class, EntityDataSerializers.COMPOUND_TAG);

    private static final Map<String, Float> trustRemainder = new ConcurrentHashMap<>();
    private static final Map<String, Float> fearRemainder = new ConcurrentHashMap<>();

    public static void addTrustFloat(EntityMaid maid, UUID playerId, float delta) {
        String key = maid.getUUID() + ":" + playerId;
        float acc = trustRemainder.getOrDefault(key, 0f) + delta;
        int intPart = (int) acc;
        if (intPart != 0) {
            addTrust(maid, playerId, intPart);
            trustRemainder.put(key, acc - intPart);
        } else {
            trustRemainder.put(key, acc);
        }
    }

    public static void addFearFloat(EntityMaid maid, UUID playerId, float delta) {
        String key = maid.getUUID() + ":" + playerId;
        float acc = fearRemainder.getOrDefault(key, 0f) + delta;
        int intPart = (int) acc;
        if (intPart != 0) {
            addFear(maid, playerId, intPart);
            fearRemainder.put(key, acc - intPart);
        } else {
            fearRemainder.put(key, acc);
        }
    }

    public static EmotionValues get(EntityMaid maid, ServerPlayer player) {
        return get(maid, player.getUUID());
    }

    public static EmotionValues get(EntityMaid maid, UUID playerId) {
        CompoundTag synced = maid.getEntityData().get(EMOTION_KEY);
        if (synced.isEmpty() && maid.getPersistentData().contains(EMOTION_TAG)) {
            // 旧存档迁移：persistentData 里的数据一次性转入同步数据
            synced = maid.getPersistentData().getCompound(EMOTION_TAG).copy();
            maid.getEntityData().set(EMOTION_KEY, synced);
        }
        if (synced.isEmpty()) {
            return EmotionValues.DEFAULT;
        }
        CompoundTag playerData = synced.getCompound(playerId.toString());
        int trust = playerData.getInt("trust");
        int fear = playerData.getInt("fear");
        if (trust == 0 && !playerData.contains("trust")) trust = 40;
        if (fear == 0 && !playerData.contains("fear")) fear = 10;
        return new EmotionValues(trust, fear);
    }

    public static void set(EntityMaid maid, UUID playerId, int trust, int fear) {
        CompoundTag synced = maid.getEntityData().get(EMOTION_KEY).copy();
        if (synced.isEmpty() && maid.getPersistentData().contains(EMOTION_TAG)) {
            synced = maid.getPersistentData().getCompound(EMOTION_TAG).copy();
        }
        CompoundTag playerData = new CompoundTag();
        playerData.putInt("trust", Math.max(0, Math.min(100, trust)));
        playerData.putInt("fear", Math.max(0, Math.min(100, fear)));
        synced.put(playerId.toString(), playerData);
        maid.getEntityData().set(EMOTION_KEY, synced);
    }

    public static void addTrust(EntityMaid maid, UUID playerId, int delta) {
        EmotionValues current = get(maid, playerId);
        int newTrust = Math.max(0, Math.min(100, current.trust + delta));
        set(maid, playerId, newTrust, current.fear);
    }

    public static void addFear(EntityMaid maid, UUID playerId, int delta) {
        EmotionValues current = get(maid, playerId);
        int newFear = Math.max(0, Math.min(100, current.fear + delta));
        set(maid, playerId, current.trust, newFear);
    }

    public static EmotionTendency getTendency(EntityMaid maid, ServerPlayer player) {
        return getTendency(maid, player.getUUID());
    }

    public static EmotionTendency getTendency(EntityMaid maid, UUID playerId) {
        EmotionValues v = get(maid, playerId);
        if (v.trust >= 70 && v.fear <= 20) return EmotionTendency.BOND;
        if (v.trust <= 20 && v.fear >= 70) return EmotionTendency.TERRIFIED;
        if (v.trust >= 40 && v.fear <= 30) return EmotionTendency.FRIENDLY;
        if (v.trust <= 30 && v.fear <= 20) return EmotionTendency.STRANGER;
        if (v.trust <= 40 && v.fear >= 50) return EmotionTendency.FEARFUL;
        if (v.trust >= 60 && v.fear >= 60) return EmotionTendency.CONFLICTED;
        return EmotionTendency.NEUTRAL;
    }

    public record EmotionValues(int trust, int fear) {
        public static final EmotionValues DEFAULT = new EmotionValues(40, 10);
    }

    public enum EmotionTendency {
        BOND, FRIENDLY, STRANGER, FEARFUL, TERRIFIED, CONFLICTED, NEUTRAL;

        private final Component name;

        EmotionTendency() {
            this.name = Component.translatable("text.callresponse.emotion." + name().toLowerCase());
        }

        public Component getName() {
            return name;
        }
    }

    public static String getTendencyPromptSuffix(EntityMaid maid, UUID playerId) {
        EmotionTendency t = getTendency(maid, playerId);
        EmotionValues v = get(maid, playerId);
        return switch (t) {
            case BOND -> "（信任" + v.trust + "，恐惧" + v.fear + "，羁绊状态：你和主人感情深厚，已经超越了普通主仆关系，你会自然地撒娇和表达爱意）";
            case FRIENDLY -> "（信任" + v.trust + "，恐惧" + v.fear + "，友好状态：你对主人很有好感，愿意亲近和依赖主人）";
            case STRANGER -> "（信任" + v.trust + "，恐惧" + v.fear + "，陌生状态：你和主人还不太熟，表现得很客气疏离）";
            case FEARFUL -> "（信任" + v.trust + "，恐惧" + v.fear + "，畏惧状态：你有点害怕主人，总是小心翼翼的）";
            case TERRIFIED -> "（信任" + v.trust + "，恐惧" + v.fear + "，恐惧状态：你对主人充满了恐惧，只想躲得远远的）";
            case CONFLICTED -> "（信任" + v.trust + "，恐惧" + v.fear + "，矛盾状态：你既依赖主人又害怕他，内心很纠结,但你一般把恐惧藏在心底，不会表露出来）";
            case NEUTRAL -> "（信任" + v.trust + "，恐惧" + v.fear + "，中立状态：你和主人维持着普通的主仆关系）";
        };
    }
}
