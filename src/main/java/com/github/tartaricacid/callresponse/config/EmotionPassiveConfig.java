package com.github.tartaricacid.callresponse.config;
import net.neoforged.neoforge.common.ModConfigSpec;

public class EmotionPassiveConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SIT_DETECTION_ENABLED;
    public static final ModConfigSpec.IntValue SIT_TRUST_CHANGE;
    public static final ModConfigSpec.IntValue SIT_FEAR_CHANGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("女仆情感被动系统配置").push("emotion_passive");

        SIT_DETECTION_ENABLED = builder
                .comment("是否启用坐姿情感变化调试")
                .define("sitDetectionEnabled", true);

        SIT_TRUST_CHANGE = builder
                .comment("坐下时信任变化值（范围 -10 ~ +10，负值减少信任）")
                .defineInRange("sitTrustChange", -2, -10, 10);

        SIT_FEAR_CHANGE = builder
                .comment("坐下时恐惧变化值（范围 -10 ~ +10，负值减少恐惧）")
                .defineInRange("sitFearChange", -2, -10, 10);

        builder.pop();
        SPEC = builder.build();
    }
}
