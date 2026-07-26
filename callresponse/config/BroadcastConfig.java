package com.github.tartaricacid.callresponse.config;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class BroadcastConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue DEBUG_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> TRIGGER_PREFIX;
    public static final ForgeConfigSpec.DoubleValue BASE_CHANCE;
    public static final ForgeConfigSpec.IntValue SEARCH_RADIUS;
    public static final ForgeConfigSpec.DoubleValue NAME_MENTION_BONUS;
    public static final ForgeConfigSpec.IntValue MAX_RESPONDERS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("女仆广播回应配置").push("broadcast");

        DEBUG_ENABLED = builder
                .comment("是否开启调试信息输出到聊天栏")
                .define("debugEnabled", false);

        TRIGGER_PREFIX = builder
                .comment("触发广播的前缀，例如输入 '#全体 去干活' 触发")
                .define("triggerPrefix", "#全体 ");

        BASE_CHANCE = builder
                .comment("每个女仆的基础回应概率 (0.0-1.0)")
                .defineInRange("baseChance", 0.4, 0.0, 1.0);

        SEARCH_RADIUS = builder
                .comment("搜索周围女仆的半径（格数）")
                .defineInRange("searchRadius", 16, 1, 64);

        NAME_MENTION_BONUS = builder
                .comment("当消息中提到女仆名字时，概率增加的量")
                .defineInRange("nameMentionBonus", 0.5, 0.0, 1.0);

        MAX_RESPONDERS = builder
                .comment("最多同时回应的女仆数量（防止API调用过多）")
                .defineInRange("maxResponders", 5, 1, 20);

        builder.pop();
        SPEC = builder.build();
    }
}