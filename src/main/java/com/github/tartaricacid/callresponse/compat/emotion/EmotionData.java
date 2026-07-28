package com.github.tartaricacid.callresponse.compat.emotion;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class EmotionData {
    private static final String EMOTION_TAG = "MaidEmotions";

    // ===== 获取情感值（ServerPlayer 版本） =====
    public static EmotionValues get(EntityMaid maid, ServerPlayer player) {
        return get(maid, player.getUUID());
    }

    // ===== 获取情感值（UUID 版本） =====
    public static EmotionValues get(EntityMaid maid, UUID playerId) {
        CompoundTag root = maid.getPersistentData();
        CompoundTag emotionMap = root.getCompound(EMOTION_TAG).orElse(new CompoundTag());
        CompoundTag playerData = emotionMap.getCompound(playerId.toString()).orElse(new CompoundTag());
        int trust = playerData.getInt("trust").orElse(40);
        int fear = playerData.getInt("fear").orElse(10);
        return new EmotionValues(trust, fear);
    }

    public static void set(EntityMaid maid, UUID playerId, int trust, int fear) {
        CompoundTag root = maid.getPersistentData();
        CompoundTag emotionMap = root.getCompound(EMOTION_TAG).orElse(new CompoundTag());
        CompoundTag playerData = new CompoundTag();
        playerData.putInt("trust", Math.clamp(trust, 0, 100));
        playerData.putInt("fear", Math.clamp(fear, 0, 100));
        emotionMap.put(playerId.toString(), playerData);
        root.put(EMOTION_TAG, emotionMap);
    }

    public static void addTrust(EntityMaid maid, UUID playerId, int delta) {
        EmotionValues current = get(maid, playerId);
        int newTrust = Math.clamp(current.trust + delta, 0, 100);
        set(maid, playerId, newTrust, current.fear);
    }

    public static void addFear(EntityMaid maid, UUID playerId, int delta) {
        EmotionValues current = get(maid, playerId);
        int newFear = Math.clamp(current.fear + delta, 0, 100);
        set(maid, playerId, current.trust, newFear);
    }

    // ===== 获取情感倾向（ServerPlayer 版本） =====
    public static EmotionTendency getTendency(EntityMaid maid, ServerPlayer player) {
        return getTendency(maid, player.getUUID());
    }

    // ===== 获取情感倾向（UUID 版本） =====
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

    public record EmotionValues(int trust, int fear) {}

    public enum EmotionTendency {
        BOND, FRIENDLY, STRANGER, FEARFUL, TERRIFIED, CONFLICTED, NEUTRAL
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