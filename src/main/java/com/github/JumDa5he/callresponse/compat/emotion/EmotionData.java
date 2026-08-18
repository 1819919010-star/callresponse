package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EmotionData {
    private static final String EMOTION_TAG = "MaidEmotions";
    private static final Map<String, Float> trustRemainder = new ConcurrentHashMap<>();
    private static final Map<String, Float> fearRemainder = new ConcurrentHashMap<>();
    private static final long CHAT_FEEDBACK_TTL_MS = 5L * 60L * 1000L;

    // 只用于识别玩家对上一轮回复的反馈；不写 NBT，重启或超时后自然清空。
    private static final Map<ChatKey, ChatFeedbackState> CHAT_FEEDBACK = new ConcurrentHashMap<>();
    private static final List<String> NEGATIVE_FEEDBACK_WORDS = List.of(
            "你没懂", "没懂", "不是这个意思", "不是", "别这样", "算了", "离谱", "无语",
            "你在说什么", "听不懂", "看不懂", "错了", "不对", "好烦", "烦死");
    private static final List<String> REPAIR_FEEDBACK_WORDS = List.of(
            "我是说", "我说的是", "重新说", "再说一遍", "不是问", "你理解错",
            "你搞错", "我问的是", "纠正");
    private static final List<String> POSITIVE_FEEDBACK_WORDS = List.of(
            "懂了", "明白了", "可以", "有用", "不错", "好耶", "太好了", "谢谢", "感谢");

    // ===== 获取情感值（ServerPlayer 版本） =====
    public static EmotionValues get(EntityMaid maid, ServerPlayer player) {
        return get(maid, player.getUUID());
    }

    // ===== 获取情感值（UUID 版本） =====
    public static EmotionValues get(EntityMaid maid, UUID playerId) {
        var e = maid.getData(InitAttachTypes.SYNCED_EMOTION).emotions().get(playerId);
        if(e == null)return EmotionValues.DEFAULT;
        return e;
    }

    public static void set(EntityMaid maid, UUID playerId, int trust, int fear) {
        var newMap = new HashMap<>(maid.getData(InitAttachTypes.SYNCED_EMOTION).emotions);
        newMap.put(playerId, new EmotionValues(trust, fear));
        maid.setData(InitAttachTypes.SYNCED_EMOTION, new MaidEmotion(Collections.unmodifiableMap(newMap)));
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

    public static void recordLastChatReply(EntityMaid maid, UUID playerId, String reply) {
        if (reply == null || reply.isBlank()) {
            return;
        }
        ChatKey key = new ChatKey(maid.getUUID(), playerId);
        ChatFeedbackState previous = getValidChatState(key);
        FeedbackKind previousFeedback = previous == null ? FeedbackKind.NONE : previous.feedback;
        CHAT_FEEDBACK.put(key, new ChatFeedbackState(reply, previousFeedback,
                System.currentTimeMillis() + CHAT_FEEDBACK_TTL_MS));
    }

    /** 只把当前玩家消息当作对该女仆上一条真实回复的反馈。 */
    public static FeedbackKind applyChatFeedback(EntityMaid maid, ServerPlayer player, String userMessage) {
        ChatKey key = new ChatKey(maid.getUUID(), player.getUUID());
        ChatFeedbackState state = getValidChatState(key);
        if (state == null || state.lastReply.isBlank()) {
            return FeedbackKind.NONE;
        }
        FeedbackKind feedback = classifyFeedback(userMessage);
        // 聊天反馈暂时只影响当轮语气，不直接改变信任数值。
        // 等对话内容与词表平衡完成后，再决定是否恢复数值变化。
        CHAT_FEEDBACK.put(key, new ChatFeedbackState(state.lastReply, feedback,
                System.currentTimeMillis() + CHAT_FEEDBACK_TTL_MS));
        return feedback;
    }

    public static String getChatFeedbackPrompt(EntityMaid maid, UUID playerId) {
        ChatFeedbackState state = getValidChatState(new ChatKey(maid.getUUID(), playerId));
        if (state == null) {
            return "";
        }
        return switch (state.feedback) {
            case NEGATIVE -> "聊天反馈：主人认为你上一轮没有听懂或答偏了。先坦率承认没接住，安抚他的情绪，再围绕这次原话重新回应；不要辩解或装懂。";
            case REPAIR -> "聊天反馈：主人正在纠正你的理解。暂停原先思路，认真听清他重新强调的对象和问题，再用自己的话确认后作答。";
            case POSITIVE -> "聊天反馈：主人认可了上一轮回复。保持当前情感语气自然接话，不要反复邀功或连续道谢。";
            case NONE -> "";
        };
    }

    private static FeedbackKind classifyFeedback(String message) {
        String text = message == null ? "" : message.trim().toLowerCase();
        if (containsAny(text, REPAIR_FEEDBACK_WORDS)) {
            return FeedbackKind.REPAIR;
        }
        if (containsAny(text, NEGATIVE_FEEDBACK_WORDS)) {
            return FeedbackKind.NEGATIVE;
        }
        if (containsAny(text, POSITIVE_FEEDBACK_WORDS)) {
            return FeedbackKind.POSITIVE;
        }
        return FeedbackKind.NONE;
    }

    private static boolean containsAny(String text, List<String> words) {
        return words.stream().anyMatch(text::contains);
    }

    private static ChatFeedbackState getValidChatState(ChatKey key) {
        ChatFeedbackState state = CHAT_FEEDBACK.get(key);
        if (state != null && state.expiresAt >= System.currentTimeMillis()) {
            return state;
        }
        CHAT_FEEDBACK.remove(key);
        return null;
    }

    public enum FeedbackKind { NONE, NEGATIVE, REPAIR, POSITIVE }

    private record ChatKey(UUID maidId, UUID playerId) {
    }

    private record ChatFeedbackState(String lastReply, FeedbackKind feedback, long expiresAt) {
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

    public record EmotionValues(int trust, int fear) {
        public static final Codec<EmotionValues> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("trust").forGetter(EmotionValues::trust),
                Codec.INT.fieldOf("fear").forGetter(EmotionValues::fear)
        ).apply(i, EmotionValues::new));
        public static final StreamCodec<ByteBuf, EmotionValues> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
        public static final EmotionValues DEFAULT = new EmotionValues(40, 10);
    }

    public record MaidEmotion(Map<UUID, EmotionValues> emotions){
        // fucking ojang
        public static final Codec<UUID> STRING_UUID_CODEC = Codec.STRING.xmap(UUID::fromString, UUID::toString);
        public static final Codec<MaidEmotion> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(STRING_UUID_CODEC, EmotionValues.CODEC).fieldOf(EMOTION_TAG).forGetter(MaidEmotion::emotions)
        ).apply(i, MaidEmotion::new));
        public static final StreamCodec<ByteBuf, MaidEmotion> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, EmotionValues.STREAM_CODEC),
                MaidEmotion::emotions,
                MaidEmotion::new
        );
        public static final MaidEmotion DEFAULT = new MaidEmotion(Map.of());
    }

    public enum EmotionTendency {
        BOND, FRIENDLY, STRANGER, FEARFUL, TERRIFIED, CONFLICTED, NEUTRAL;

        private final Component name;
        EmotionTendency() {
            this.name = Component.translatable("text.callresponse.emotion." + name().toLowerCase());
        }

        public Component getName(){
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
