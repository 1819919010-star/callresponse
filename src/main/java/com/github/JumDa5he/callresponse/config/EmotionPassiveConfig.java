package com.github.JumDa5he.callresponse.config;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class EmotionPassiveConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SIT_DETECTION_ENABLED;
    public static final ModConfigSpec.IntValue SIT_TRUST_CHANGE;
    public static final ModConfigSpec.IntValue SIT_FEAR_CHANGE;
    public static final ModConfigSpec.IntValue WANDERING_MAID_INTERVAL_MINUTES;
    public static final ModConfigSpec.IntValue WANDERING_MAID_COUNT;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WANDERING_MAID_DROP_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WANDERING_MAID_DROP_WHITELIST;
    public static final ModConfigSpec.BooleanValue WANDERING_TRADER_MAID_TRADE_ENABLED;
    public static final ModConfigSpec.IntValue DOTING_POSSESSIVE_INTERVAL_MINUTES;
    public static final ModConfigSpec.IntValue DOTING_POSSESSIVE_DURATION_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("女仆情感被动系统配置")
                .translation("callresponse.configuration.emotion_passive").push("emotion_passive");

        SIT_DETECTION_ENABLED = builder
                .comment("是否启用坐姿情感变化调试")
                .translation("callresponse.configuration.sitDetectionEnabled")
                .define("sitDetectionEnabled", false);

        SIT_TRUST_CHANGE = builder
                .comment("坐下时信任变化值（范围 -10 ~ +10，负值减少信任）")
                .translation("callresponse.configuration.sitTrustChange")
                .defineInRange("sitTrustChange", -2, -10, 10);

        SIT_FEAR_CHANGE = builder
                .comment("坐下时恐惧变化值（范围 -10 ~ +10，负值减少恐惧）")
                .translation("callresponse.configuration.sitFearChange")
                .defineInRange("sitFearChange", -2, -10, 10);

        builder.pop();

        builder.comment("流浪女仆事件配置")
                .translation("callresponse.configuration.wanderingMaid").push("wandering_maid");
        WANDERING_MAID_INTERVAL_MINUTES = builder
                .comment("每隔多少分钟尝试触发一次流浪女仆事件（范围 5~30 分钟）")
                .translation("callresponse.configuration.wanderingMaidInterval")
                .defineInRange("spawnIntervalMinutes", 5, 5, 30);
        WANDERING_MAID_COUNT = builder
                .comment("每名玩家在一次流浪事件中生成的女仆数量（范围 1~10）")
                .translation("callresponse.configuration.wanderingMaidCount")
                .defineInRange("maidsPerPlayer", 1, 1, 10);
        WANDERING_MAID_DROP_BLACKLIST = builder
                .comment("流浪女仆死亡掉落与收留赠礼的物品黑名单。填写完整物品 ID，例如 minecraft:bedrock；可无限添加，黑名单优先于白名单")
                .translation("callresponse.configuration.wanderingDropBlacklist")
                .defineListAllowEmpty("dropBlacklist", List::of, value -> value instanceof String);
        WANDERING_MAID_DROP_WHITELIST = builder
                .comment("流浪女仆死亡掉落与收留赠礼的物品白名单。留空时允许所有非黑名单物品；填写后只会随机其中的物品，例如 minecraft:diamond")
                .translation("callresponse.configuration.wanderingDropWhitelist")
                .defineListAllowEmpty("dropWhitelist", List::of, value -> value instanceof String);
        builder.pop();

        builder.comment("流浪商人女仆交易配置")
                .translation("callresponse.configuration.wanderingTraderTrade").push("wandering_trader_maid_trade");
        WANDERING_TRADER_MAID_TRADE_ENABLED = builder
                .comment("整套流浪商人女仆交易的总开关。关闭后不生成待售女仆、不添加呼应物品交易，也不显示交易女仆按钮")
                .translation("callresponse.configuration.wanderingTraderTradeEnabled")
                .define("enabled", true);
        builder.pop();

        builder.comment("溺爱女仆占有欲配置")
                .translation("callresponse.configuration.dotingPossessive").push("doting_possessive");
        DOTING_POSSESSIVE_INTERVAL_MINUTES = builder
                .comment("溺爱女仆多久尝试一次赶走主人身边的其他女仆（范围 0~10 分钟；0 为只要附近有其他女仆就持续追赶）")
                .translation("callresponse.configuration.dotingPossessiveInterval")
                .defineInRange("intervalMinutes", 1, 0, 10);
        DOTING_POSSESSIVE_DURATION_SECONDS = builder
                .comment("每次占有欲发作后持续赶走其他女仆的时间（范围 10~120 秒）")
                .translation("callresponse.configuration.dotingPossessiveDuration")
                .defineInRange("durationSeconds", 20, 10, 120);
        builder.pop();
        SPEC = builder.build();
    }
}
