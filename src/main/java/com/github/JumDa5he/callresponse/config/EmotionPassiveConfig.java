package com.github.JumDa5he.callresponse.config;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class EmotionPassiveConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SIT_DETECTION_ENABLED;
    public static final ForgeConfigSpec.IntValue SIT_TRUST_CHANGE;
    public static final ForgeConfigSpec.IntValue SIT_FEAR_CHANGE;
    public static final ForgeConfigSpec.IntValue WANDERING_MAID_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.IntValue WANDERING_MAID_SPAWN_CHANCE;
    public static final ForgeConfigSpec.IntValue WANDERING_MAID_COUNT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WANDERING_MAID_DROP_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WANDERING_MAID_DROP_WHITELIST;
    public static final ForgeConfigSpec.BooleanValue WANDERING_TRADER_MAID_TRADE_ENABLED;
    public static final ForgeConfigSpec.IntValue DOTING_POSSESSIVE_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.IntValue DOTING_POSSESSIVE_DURATION_SECONDS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.translation("callresponse.configuration.emotion_passive").push("emotion_passive");

        SIT_DETECTION_ENABLED = builder
                .translation("callresponse.configuration.sitDetectionEnabled")
                .define("sitDetectionEnabled", false);

        SIT_TRUST_CHANGE = builder
                .translation("callresponse.configuration.sitTrustChange")
                .defineInRange("sitTrustChange", -2, -10, 10);

        SIT_FEAR_CHANGE = builder
                .translation("callresponse.configuration.sitFearChange")
                .defineInRange("sitFearChange", -2, -10, 10);

        builder.pop();

        builder.translation("callresponse.configuration.wanderingMaid").push("wandering_maid");
        WANDERING_MAID_INTERVAL_MINUTES = builder
                .translation("callresponse.configuration.wanderingMaidInterval")
                .defineInRange("spawnIntervalMinutes", 5, 0, 60);
        WANDERING_MAID_SPAWN_CHANCE = builder
                .translation("callresponse.configuration.wanderingMaidSpawnChance")
                .defineInRange("spawnChancePercent", 25, 0, 100);
        WANDERING_MAID_COUNT = builder
                .translation("callresponse.configuration.wanderingMaidCount")
                .defineInRange("maidsPerPlayer", 1, 1, 10);
        WANDERING_MAID_DROP_BLACKLIST = builder
                .translation("callresponse.configuration.wanderingDropBlacklist")
                .defineListAllowEmpty("dropBlacklist", List::of, value -> value instanceof String);
        WANDERING_MAID_DROP_WHITELIST = builder
                .translation("callresponse.configuration.wanderingDropWhitelist")
                .defineListAllowEmpty("dropWhitelist", List::of, value -> value instanceof String);
        builder.pop();

        builder.translation("callresponse.configuration.wanderingTraderTrade").push("wandering_trader_maid_trade");
        WANDERING_TRADER_MAID_TRADE_ENABLED = builder
                .translation("callresponse.configuration.wanderingTraderTradeEnabled")
                .define("enabled", true);
        builder.pop();

        builder.translation("callresponse.configuration.dotingPossessive").push("doting_possessive");
        DOTING_POSSESSIVE_INTERVAL_MINUTES = builder
                .translation("callresponse.configuration.dotingPossessiveInterval")
                .defineInRange("intervalMinutes", 1, 0, 10);
        DOTING_POSSESSIVE_DURATION_SECONDS = builder
                .translation("callresponse.configuration.dotingPossessiveDuration")
                .defineInRange("durationSeconds", 20, 10, 120);
        builder.pop();
        SPEC = builder.build();
    }
}
