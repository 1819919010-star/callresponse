package com.github.JumDa5he.callresponse.config;
import com.github.JumDa5he.callresponse.compat.damage.ProtectionBreakLevel;
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
    public static final ForgeConfigSpec.IntValue API_CALLS_PER_MINUTE;
    public static final ForgeConfigSpec.BooleanValue OWNER_DAMAGE_BYPASS_ENABLED;
    public static final ForgeConfigSpec.EnumValue<ProtectionBreakLevel> OWNER_DAMAGE_PROTECTION_BREAK_LEVEL;
    public static final ForgeConfigSpec.BooleanValue NPC_EVENT_AI_REPLY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue BETRAYAL_MAID_EXPLOSION_BREAK_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue TALK_EVENT_ENABLED;
    public static final ForgeConfigSpec.IntValue TALK_EVENT_MIN_MAIDS;
    public static final ForgeConfigSpec.IntValue TALK_REPLY_INTERVAL_SECONDS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.translation("callresponse.configuration.broadcast").push("broadcast");

        DEBUG_ENABLED = builder
                .translation("callresponse.configuration.debugEnabled")
                .define("debugEnabled", false);

        TRIGGER_PREFIX = builder
                .translation("callresponse.configuration.triggerPrefix")
                .define("triggerPrefix", "#全体 ");

        BASE_CHANCE = builder
                .translation("callresponse.configuration.baseChance")
                .defineInRange("baseChance", 0.4, 0.0, 1.0);

        SEARCH_RADIUS = builder
                .translation("callresponse.configuration.searchRadius")
                .defineInRange("searchRadius", 16, 1, 64);

        NAME_MENTION_BONUS = builder
                .translation("callresponse.configuration.nameMentionBonus")
                .defineInRange("nameMentionBonus", 0.5, 0.0, 1.0);

        MAX_RESPONDERS = builder
                .translation("callresponse.configuration.maxResponders")
                .defineInRange("maxResponders", 5, 1, 20);

        API_CALLS_PER_MINUTE = builder
                .translation("callresponse.configuration.apiCallsPerMinute")
                .defineInRange("apiCallsPerMinute", 20, 0, 300);

        OWNER_DAMAGE_BYPASS_ENABLED = builder
                .translation("callresponse.configuration.ownerDamageBypassEnabled")
                .define("ownerDamageBypassEnabled", true);

        OWNER_DAMAGE_PROTECTION_BREAK_LEVEL = builder
                .translation("callresponse.configuration.ownerDamageProtectionBreakLevel")
                .defineEnum("ownerDamageProtectionBreakLevel", ProtectionBreakLevel.ULTIMATE);

        builder.pop();

        builder.translation("callresponse.configuration.betrayal").push("betrayal");
        BETRAYAL_MAID_EXPLOSION_BREAK_BLOCKS = builder
                .translation("callresponse.configuration.betrayalMaidExplosionBreakBlocks")
                .define("betrayalMaidExplosionBreakBlocks", true);
        builder.pop();

        builder.translation("callresponse.configuration.npcEvent").push("npc_event");
        NPC_EVENT_AI_REPLY_ENABLED = builder
                .translation("callresponse.configuration.enableNpcEventAiReply")
                .define("enableNpcEventAiReply", true);
        builder.pop();

        builder.translation("callresponse.configuration.talkEvent").push("talk_event");
        TALK_EVENT_ENABLED = builder
                .translation("callresponse.configuration.talkEventEnabled")
                .define("enabled", true);
        TALK_EVENT_MIN_MAIDS = builder
                .translation("callresponse.configuration.talkEventMinMaids")
                .defineInRange("minMaids", 5, 2, 10);
        TALK_REPLY_INTERVAL_SECONDS = builder
                .translation("callresponse.configuration.talkReplyIntervalSeconds")
                .defineInRange("replyIntervalSeconds", 1, 1, 20);
        builder.pop();
        SPEC = builder.build();
    }
}
