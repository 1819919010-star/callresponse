package com.github.JumDa5he.callresponse.config;
import com.github.JumDa5he.callresponse.compat.damage.ProtectionBreakLevel;
import net.neoforged.neoforge.common.ModConfigSpec;

public class BroadcastConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue DEBUG_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> TRIGGER_PREFIX;
    public static final ModConfigSpec.DoubleValue BASE_CHANCE;
    public static final ModConfigSpec.IntValue SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue NAME_MENTION_BONUS;
    public static final ModConfigSpec.IntValue MAX_RESPONDERS;
    public static final ModConfigSpec.IntValue API_CALLS_PER_MINUTE;
    public static final ModConfigSpec.BooleanValue OWNER_DAMAGE_BYPASS_ENABLED;
    public static final ModConfigSpec.EnumValue<ProtectionBreakLevel> OWNER_DAMAGE_PROTECTION_BREAK_LEVEL;
    public static final ModConfigSpec.BooleanValue NPC_EVENT_AI_REPLY_ENABLED;
    public static final ModConfigSpec.BooleanValue BETRAYAL_MAID_EXPLOSION_BREAK_BLOCKS;
    public static final ModConfigSpec.BooleanValue TALK_EVENT_ENABLED;
    public static final ModConfigSpec.IntValue TALK_EVENT_MIN_MAIDS;
    public static final ModConfigSpec.IntValue TALK_REPLY_INTERVAL_SECONDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("女仆广播回应配置")
                .translation("callresponse.configuration.broadcast").push("broadcast");

        DEBUG_ENABLED = builder
                .comment("是否开启调试信息输出到聊天栏")
                .translation("callresponse.configuration.debugEnabled")
                .define("debugEnabled", false);

        TRIGGER_PREFIX = builder
                .comment("触发广播的前缀，例如输入 '#全体 去干活' 触发")
                .translation("callresponse.configuration.triggerPrefix")
                .define("triggerPrefix", "#全体 ");

        BASE_CHANCE = builder
                .comment("每个女仆的基础回应概率 (0.0-1.0)")
                .translation("callresponse.configuration.baseChance")
                .defineInRange("baseChance", 0.4, 0.0, 1.0);

        SEARCH_RADIUS = builder
                .comment("搜索周围女仆的半径（格数）")
                .translation("callresponse.configuration.searchRadius")
                .defineInRange("searchRadius", 16, 1, 64);

        NAME_MENTION_BONUS = builder
                .comment("当消息中提到女仆名字时，概率增加的量")
                .translation("callresponse.configuration.nameMentionBonus")
                .defineInRange("nameMentionBonus", 0.5, 0.0, 1.0);

        MAX_RESPONDERS = builder
                .comment("最多同时回应的女仆数量（防止API调用过多）")
                .translation("callresponse.configuration.maxResponders")
                .defineInRange("maxResponders", 5, 1, 20);

        API_CALLS_PER_MINUTE = builder
                .comment("《呼应》每分钟最多发起多少次 AI 请求；0 表示不限额。只限制广播、主动对话和女仆谈话，不影响 TLM 本体普通聊天")
                .translation("callresponse.configuration.apiCallsPerMinute")
                .defineInRange("apiCallsPerMinute", 20, 0, 300);

        OWNER_DAMAGE_BYPASS_ENABLED = builder
                .comment("主人伤害保护破除的总开关；关闭后等级配置无效并完全走正常 TLM/模组伤害逻辑")
                .translation("callresponse.configuration.ownerDamageBypassEnabled")
                .define("ownerDamageBypassEnabled", true);

        OWNER_DAMAGE_PROTECTION_BREAK_LEVEL = builder
                .comment("主人攻击自己女仆时的保护破除等级：NONE=关闭，BASIC=仅保证进入正常受伤流程，ULTIMATE=沿用原有全破效果")
                .translation("callresponse.configuration.ownerDamageProtectionBreakLevel")
                .defineEnum("ownerDamageProtectionBreakLevel", ProtectionBreakLevel.ULTIMATE);

        builder.pop();

        builder.comment("背叛女仆配置")
                .translation("callresponse.configuration.betrayal").push("betrayal");
        BETRAYAL_MAID_EXPLOSION_BREAK_BLOCKS = builder
                .comment("背叛女仆死亡爆炸是否破坏地形")
                .translation("callresponse.configuration.betrayalMaidExplosionBreakBlocks")
                .define("betrayalMaidExplosionBreakBlocks", true);
        builder.pop();

        builder.comment("NPC 日常事件配置")
                .translation("callresponse.configuration.npcEvent").push("npc_event");
        NPC_EVENT_AI_REPLY_ENABLED = builder
                .comment("是否允许 NPC 事件沿用各自现有的 AI/固定回复混合逻辑；关闭后所有事件选项都只使用内置固定台词")
                .translation("callresponse.configuration.enableNpcEventAiReply")
                .define("enableNpcEventAiReply", true);
        builder.pop();

        builder.comment("女仆谈话事件配置")
                .translation("callresponse.configuration.talkEvent").push("talk_event");
        TALK_EVENT_ENABLED = builder
                .comment("是否启用女仆自动聚集谈话事件。关闭后不会自动触发，也不能用指令强制开启")
                .translation("callresponse.configuration.talkEventEnabled")
                .define("enabled", true);
        TALK_EVENT_MIN_MAIDS = builder
                .comment("同一主人在同一区块内至少需要多少只符合条件的女仆才会开始谈话（范围 2~10）")
                .translation("callresponse.configuration.talkEventMinMaids")
                .defineInRange("minMaids", 5, 2, 10);
        TALK_REPLY_INTERVAL_SECONDS = builder
                .comment("谈话中一名女仆说完后，下一名女仆最少等待多少秒再回话（范围 1~20 秒）")
                .translation("callresponse.configuration.talkReplyIntervalSeconds")
                .defineInRange("replyIntervalSeconds", 1, 1, 20);
        builder.pop();
        SPEC = builder.build();
    }
}
