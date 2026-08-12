package com.github.JumDa5he.callresponse.config;
import net.neoforged.neoforge.common.ModConfigSpec;

public class EmotionPassiveConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SIT_DETECTION_ENABLED;
    public static final ModConfigSpec.IntValue SIT_TRUST_CHANGE;
    public static final ModConfigSpec.IntValue SIT_FEAR_CHANGE;
    public static final ModConfigSpec.IntValue WANDERING_MAID_INTERVAL_MINUTES;
    public static final ModConfigSpec.IntValue WANDERING_MAID_COUNT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("女仆情感被动系统配置").push("emotion_passive");

        SIT_DETECTION_ENABLED = builder
                .comment("是否启用坐姿情感变化调试")
                .define("sitDetectionEnabled", false);

        SIT_TRUST_CHANGE = builder
                .comment("坐下时信任变化值（范围 -10 ~ +10，负值减少信任）")
                .defineInRange("sitTrustChange", -2, -10, 10);

        SIT_FEAR_CHANGE = builder
                .comment("坐下时恐惧变化值（范围 -10 ~ +10，负值减少恐惧）")
                .defineInRange("sitFearChange", -2, -10, 10);

        builder.pop();

        builder.comment("流浪女仆事件配置").push("wandering_maid");
        WANDERING_MAID_INTERVAL_MINUTES = builder
                .comment("每隔多少分钟尝试触发一次流浪女仆事件（范围 5~30 分钟）")
                .defineInRange("spawnIntervalMinutes", 5, 5, 30);
        WANDERING_MAID_COUNT = builder
                .comment("每名玩家在一次流浪事件中生成的女仆数量（范围 1~10）")
                .defineInRange("maidsPerPlayer", 1, 1, 10);
        builder.pop();
        SPEC = builder.build();
    }
}
