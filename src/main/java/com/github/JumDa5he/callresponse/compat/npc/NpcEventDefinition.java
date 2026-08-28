package com.github.JumDa5he.callresponse.compat.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * NPC 日常事件的纯数据定义。所有可见文字均保存翻译键，不在 Java 中硬编码。
 */
public record NpcEventDefinition(String id, Type type, String condition, int priority,
                                 String category, long cooldownTicks, int weight,
                                 double conditionValue, double conditionValue2,
                                 EmotionCondition emotionCondition,
                                 String titleKey, String descriptionKey,
                                 List<Option> options) {
    public static NpcEventDefinition fromJson(JsonObject json) {
        String id = requiredString(json, "id");
        Type type = Type.parse(requiredString(json, "type"));
        String condition = getString(json, "condition", "");
        int priority = getInt(json, "priority", 10);
        String category = getString(json, "category", "daily");
        long cooldownTicks = Math.max(0L, getLong(json, "cooldownTicks", 0L));
        int weight = Math.max(1, getInt(json, "weight", 1));
        double conditionValue = getDouble(json, "conditionValue", 0.0D);
        double conditionValue2 = getDouble(json, "conditionValue2", 0.0D);
        EmotionCondition emotion = json.has("emotionCondition")
                ? EmotionCondition.fromJson(json.getAsJsonObject("emotionCondition")) : null;
        String title = requiredString(json, "title");
        String description = requiredString(json, "description");
        JsonArray optionArray = json.getAsJsonArray("options");
        if (optionArray == null || optionArray.isEmpty()) {
            throw new IllegalArgumentException("事件 " + id + " 至少需要一个选项");
        }
        List<Option> options = new ArrayList<>();
        for (JsonElement element : optionArray) {
            options.add(Option.fromJson(element.getAsJsonObject()));
        }
        return new NpcEventDefinition(id, type, condition, priority, category,
                cooldownTicks, weight, conditionValue, conditionValue2, emotion,
                title, description, List.copyOf(options));
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            throw new IllegalArgumentException("缺少字符串字段: " + key);
        }
        String value = json.get(key).getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("字段不能为空: " + key);
        }
        return value;
    }

    private static String getString(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static long getLong(JsonObject json, String key, long fallback) {
        return json.has(key) ? json.get(key).getAsLong() : fallback;
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    public enum Type {
        CONDITIONAL, RANDOM;

        static Type parse(String value) {
            return "random".equalsIgnoreCase(value) ? RANDOM : CONDITIONAL;
        }
    }

    public record EmotionCondition(EmotionType type, int min, int max) {
        static EmotionCondition fromJson(JsonObject json) {
            EmotionType type = EmotionType.valueOf(requiredString(json, "type")
                    .toUpperCase(Locale.ROOT));
            int min = Math.max(0, Math.min(100, getInt(json, "min", 0)));
            int max = Math.max(min, Math.min(100, getInt(json, "max", 100)));
            return new EmotionCondition(type, min, max);
        }
    }

    public enum EmotionType {
        TRUST, FEAR
    }

    public record Option(String textKey, int trust, int fear, int hunger,
                         int favor, String responseKey, boolean aiResponse,
                         String action) {
        static Option fromJson(JsonObject json) {
            return new Option(requiredString(json, "text"),
                    getInt(json, "trust", 0), getInt(json, "fear", 0),
                    getInt(json, "hunger", 0), getInt(json, "favor", 0),
                    getString(json, "response", "").trim(),
                    !json.has("aiResponse") || json.get("aiResponse").getAsBoolean(),
                    getString(json, "action", "").trim());
        }
    }
}
