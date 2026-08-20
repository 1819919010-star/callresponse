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
        builder.comment("女仆派遣外出系统配置")
                .translation("callresponse.configuration.dispatch").push("dispatch");
        MAX_ACTIVE_DISPATCHES = builder
                .comment("每名玩家最多同时派遣多少只女仆（范围 1~10）")
                .translation("callresponse.configuration.dispatchMaxActive")
                .defineInRange("maxActiveDispatches", 3, 1, 10);
        EVENT_REFRESH_MINUTES = builder
                .comment("派遣事件列表每隔多少现实分钟刷新（范围 1~60）")
                .translation("callresponse.configuration.dispatchRefreshMinutes")
                .defineInRange("eventRefreshMinutes", 10, 1, 60);
        EVENT_COUNT_MIN = builder
                .comment("每类事件刷新时最少显示数量（范围 1~10）")
                .translation("callresponse.configuration.dispatchEventCountMin")
                .defineInRange("eventCountMin", 3, 1, 10);
        EVENT_COUNT_MAX = builder
                .comment("每类事件刷新时最多显示数量（范围 1~10，不低于最少数量时生效）")
                .translation("callresponse.configuration.dispatchEventCountMax")
                .defineInRange("eventCountMax", 5, 1, 10);
        CROSS_WORLD_RECOVERY = builder
                .comment("是否允许从其他存档回收已派遣女仆。开启后会使用 config/callresponse/dispatch_global.json")
                .translation("callresponse.configuration.dispatchCrossWorld")
                .define("crossWorldRecovery", false);
        builder.pop();
        SPEC = builder.build();
    }

    private DispatchConfig() {
    }
}
