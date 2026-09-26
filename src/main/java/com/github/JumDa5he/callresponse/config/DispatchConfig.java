package com.github.JumDa5he.callresponse.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** 女仆派遣系统配置。 */
public final class DispatchConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE_DISPATCHES;
    public static final ForgeConfigSpec.IntValue EVENT_REFRESH_MINUTES;
    public static final ForgeConfigSpec.IntValue EVENT_COUNT_MIN;
    public static final ForgeConfigSpec.IntValue EVENT_COUNT_MAX;
    public static final ForgeConfigSpec.BooleanValue CROSS_WORLD_RECOVERY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.translation("callresponse.configuration.dispatch").push("dispatch");
        MAX_ACTIVE_DISPATCHES = builder
                .translation("callresponse.configuration.dispatchMaxActive")
                .defineInRange("maxActiveDispatches", 3, 1, 10);
        EVENT_REFRESH_MINUTES = builder
                .translation("callresponse.configuration.dispatchRefreshMinutes")
                .defineInRange("eventRefreshMinutes", 10, 1, 60);
        EVENT_COUNT_MIN = builder
                .translation("callresponse.configuration.dispatchEventCountMin")
                .defineInRange("eventCountMin", 3, 1, 10);
        EVENT_COUNT_MAX = builder
                .translation("callresponse.configuration.dispatchEventCountMax")
                .defineInRange("eventCountMax", 5, 1, 10);
        CROSS_WORLD_RECOVERY = builder
                .translation("callresponse.configuration.dispatchCrossWorld")
                .define("crossWorldRecovery", false);
        builder.pop();
        SPEC = builder.build();
    }

    private DispatchConfig() {
    }
}
