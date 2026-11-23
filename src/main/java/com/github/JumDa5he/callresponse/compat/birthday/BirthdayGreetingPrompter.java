package com.github.JumDa5he.callresponse.compat.birthday;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.api.AuthorUtil;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.DefaultLLMSite;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMSite;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.openai.LLMOpenAISite;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.StringUtils;

import java.net.http.HttpRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 生日祝福的提示词来源。
 *
 * <p>当大模型可用时，先用一次轻量请求让 AI 依据玩家/女仆信息撰写这次的提示词，
 * 再把它交给女仆自己的对话流程；AI 不可用、报错或超时都回退到内置提示词，
 * 保证庆祝流程不会因为网络问题而中断。</p>
 */
public final class BirthdayGreetingPrompter {
    /** 普通玩家的内置兜底提示词，同时也是 AI 生成失败时的落点。 */
    public static final String FALLBACK_PROMPT = "今天过生日的是主人，不是你。你正在给主人庆祝生日，请对主人本人送上1～2句真挚的生日祝福。";
    /** 作者彩蛋的内置兜底提示词。 */
    public static final String FALLBACK_PROMPT_IFOX =
            "今天过生日的是主人，不是你。你是送祝福的一方。主人平时总爱抱着酒狐到处跑，请向他送上温柔又带点调侃的生日祝福。";

    private static final Pattern MAID_BIRTHDAY = Pattern.compile(
            "(?:今天|今日|今儿)(?:是|是我过|轮到)?(?:我|自己)的?生日"
                    + "|(?:谢谢|感谢).{0,12}(?:主人|你).{0,8}(?:给|为|陪)我.{0,6}(?:过生日|庆生|庆祝生日)"
                    + "|(?:主人|你).{0,6}陪我.{0,6}过生日|\\bmy birthday\\b", Pattern.CASE_INSENSITIVE);

    /** AI 写提示词的等待上限，超过就直接用兜底提示词。 */
    private static final long PROMPT_TIMEOUT_MILLIS = 4000L;
    private static final int MAX_PROMPT_LENGTH = 200;

    /**
     * 一套提示词模板。除内置兜底外都带 {@code %d/%s} 占位符，
     * 依次是月、日、主人名、女仆名。
     */
    private record PromptSet(String fallback, String systemZh, String systemEn, String userZh, String userEn) {
    }

    private static final PromptSet NORMAL = new PromptSet(
            FALLBACK_PROMPT,
            """
            你是游戏内 AI 女仆的台词提示词撰写助手。
            请只输出提示词正文，不要引号、不要解释、不要换行，长度控制在 1~2 句。""",
            """
            You write prompts for an AI maid inside a game.
            Output only the prompt text: no quotes, no explanation, no line breaks, 1-2 sentences.""",
            "今天是 %d 月 %d 日，是主人「%s」的生日。女仆「%s」想为他庆祝。"
                    + "请写一条自然、亲昵、符合女仆口吻的提示词，让女仆向主人送上生日祝福。",
            "Today is %d/%d, the birthday of Master \"%s\". Maid \"%s\" wants to celebrate. "
                    + "Write a warm, natural prompt telling the maid to wish her master a happy birthday in her own voice.");

    /**
     * 作者彩蛋：i 狐区的玩家生日时换成专属提示词，
     * 与 {@code BetrayalOutpostMaidData} 里的 ifox 台词池是同一套彩蛋身份。
     */
    private static final PromptSet IFOX = new PromptSet(
            FALLBACK_PROMPT_IFOX,
            """
            你是游戏内 AI 女仆的台词提示词撰写助手。
            请只输出提示词正文，不要引号、不要解释、不要换行，长度控制在 1~2 句。
            这位主人是模组圈里出了名的「酒狐玩家」，语气可以亲近一点，带一点点调侃。""",
            """
            You write prompts for an AI maid inside a game.
            Output only the prompt text: no quotes, no explanation, no line breaks, 1-2 sentences.
            This master is a well-known wine-fox lover in the modding circle, so a warm but slightly teasing tone is welcome.""",
            "今天是 %d 月 %d 日，是主人「%s」的生日。女仆「%s」想为他庆祝。"
                    + "这位主人平时总爱在你身上实验各种东西，例如丢出去，关笼子里，挂牌子示众。"
                    + "请在向主人送上生日祝福的同时调侃一下他。",
            "Today is %d/%d, the birthday of Master \"%s\". Maid \"%s\" wants to celebrate. "
                    + "This master is famous for always carrying a wine fox around. "
                    + "Write a warm, playful prompt with a light tease, telling the maid to wish her master a happy birthday.");

    /**
     * 作者彩蛋身份走 IFOX 提示词，其余玩家走 NORMAL。
     * 优先看女仆的主人，和「女仆认出主人」的语义一致；拿不到主人时退回传入的玩家。
     */
    private static PromptSet promptSet(ServerPlayer player, EntityMaid maid) {
        Player owner = maid.getOwner() instanceof Player value ? value : player;
        return AuthorUtil.isLoveWineFoxTV(owner) ? IFOX : NORMAL;
    }

    private BirthdayGreetingPrompter() {
    }

    /**
     * 触发一次生日祝福。AI 可用时提示词由 AI 撰写，否则直接使用内置提示词。
     *
     * @return 是否真的会让女仆开口（AI 完全不可用时返回 false，只保留气泡与粒子庆祝）
     */
    public static boolean greet(ServerPlayer player, EntityMaid maid, LocalDate date) {
        LLMClient client = resolveClient(maid);
        if (client == null) {
            return false;
        }

        AtomicBoolean delivered = new AtomicBoolean(false);
        PromptSet variant = promptSet(player, maid);
        // 兜底：AI 迟迟不回时也要让女仆说话，避免庆祝卡在静默里。
        CompletableFuture.runAsync(
                () -> deliver(player, maid, variant.fallback(), delivered),
                CompletableFuture.delayedExecutor(PROMPT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

        try {
            List<LLMMessage> messages = List.of(
                    LLMMessage.systemChat(maid, systemPrompt(player, variant)),
                    LLMMessage.userChat(maid, userPrompt(player, maid, date, variant)));
            client.chat(new PromptCallback(maid.getAiChatManager(), messages, player, maid, delivered,
                    variant.fallback()));
        } catch (Exception exception) {
            CallResponseMod.LOGGER.error("[生日] 请求 AI 撰写提示词失败，改用内置提示词", exception);
            deliver(player, maid, variant.fallback(), delivered);
        }
        return true;
    }

    private static String systemPrompt(ServerPlayer player, PromptSet promptSet) {
        return isChinese(player) ? promptSet.systemZh() : promptSet.systemEn();
    }

    private static String userPrompt(ServerPlayer player, EntityMaid maid, LocalDate date, PromptSet promptSet) {
        String ownerName = player.getName().getString();
        String maidName = maid.getName().getString();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        String template = isChinese(player) ? promptSet.userZh() : promptSet.userEn();
        return ownershipFacts(player) + "\n" + template.formatted(month, day, ownerName, maidName);
    }

    static String ownershipFacts(ServerPlayer player) {
        String name = player.getName().getString();
        return isChinese(player)
                ? "今天过生日的是你的主人「" + name + "」，也就是当前玩家。不是你自己或其他女仆的生日。"
                    + "你是给主人送祝福的一方：按现有人格和情感对主人本人说1～2句生日祝福，"
                    + "不要说‘谢谢主人给我过生日’、‘今天是我的生日’或‘主人来陪我过生日’。"
                : "Today is the birthday of your master, the current player \"" + name
                    + "\". It is NOT your birthday or another maid's birthday. You are wishing your master a happy birthday. "
                    + "Use your own personality and feelings for 1-2 sentences addressed to your master. "
                    + "Do not say it is my birthday or thank your master for celebrating your birthday.";
    }

    static boolean isMaidBirthdayReply(String text) {
        if (text == null) return true;
        return MAID_BIRTHDAY.matcher(text.toLowerCase(Locale.ROOT).replaceAll("[\\s，,。.!！?？、]", "")).find()
                || Pattern.compile("\\bmy birthday\\b", Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    private static boolean isChinese(ServerPlayer player) {
        String language = player.getLanguage();
        return language != null && language.toLowerCase().startsWith("zh");
    }

    /** 女仆是否具备可用的大模型站点；没有就只保留气泡庆祝。 */
    private static LLMClient resolveClient(EntityMaid maid) {
        if (!AIConfig.LLM_ENABLED.get()) {
            return null;
        }
        MaidAIChatManager chatManager = maid.getAiChatManager();
        if (chatManager == null) {
            return null;
        }
        LLMSite site = chatManager.getLLMSite();
        if (site == null || !site.enabled()) {
            return null;
        }
        // 默认 DeepSeek 站点还没填密钥时等价于不可用，交给气泡庆祝即可。
        if (site instanceof LLMOpenAISite openAISite
                && site.id().equals(DefaultLLMSite.DEEPSEEK.id())
                && StringUtils.isBlank(openAISite.secretKey())) {
            return null;
        }
        return site.client();
    }

    private static void deliver(ServerPlayer player, EntityMaid maid, String prompt, AtomicBoolean delivered) {
        if (!delivered.compareAndSet(false, true)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        server.submit(() -> {
            if (player.hasDisconnected() || maid.isRemoved() || !maid.isAlive()) {
                return;
            }
            if (!(maid.level() instanceof ServerLevel level) || level.getEntity(maid.getUUID()) == null) {
                return;
            }
            try {
                MaidResponder.replyToEvent(player, maid, ownershipFacts(player) + "\n" + prompt,
                        Component.translatable("bubble.callresponse.birthday.1", player.getName()),
                        value -> value.isAlive() && !value.isRemoved() && value.isTame()
                                && player.getUUID().equals(value.getOwnerUUID()) && value.level() == player.level()
                                && !com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager.isIntimidated(value)
                                && !com.github.JumDa5he.callresponse.compat.talk.TalkEventManager.isParticipant(value),
                        text -> !isMaidBirthdayReply(text));
            } catch (Exception exception) {
                CallResponseMod.LOGGER.error("[生日] 女仆生日祝福发送失败", exception);
            }
        });
    }

    /** 去掉模型偶尔附带的引号、换行和前后缀，只留下可用的提示词正文。 */
    static String sanitize(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String text = raw.replace('\r', ' ').replace('\n', ' ').trim();
        text = text.replaceAll("^[\"'“”‘’]+", "").replaceAll("[\"'“”‘’]+$", "").trim();
        text = text.replaceAll("\\s{2,}", " ");
        if (text.length() > MAX_PROMPT_LENGTH) {
            text = text.substring(0, MAX_PROMPT_LENGTH).trim();
        }
        return text;
    }

    /**
     * 只借用本体的 LLM 通道来写提示词：不写历史、不显示气泡、不带工具，
     * 避免污染女仆的正常对话记忆。
     */
    private static final class PromptCallback extends LLMCallback {
        private final ServerPlayer player;
        private final EntityMaid maid;
        private final AtomicBoolean delivered;
        private final String fallback;

        private PromptCallback(MaidAIChatManager chatManager, List<LLMMessage> messages,
                               ServerPlayer player, EntityMaid maid, AtomicBoolean delivered,
                               String fallback) {
            // subagents = true：不创建“思考中”气泡。
            super(chatManager, messages, true);
            this.player = player;
            this.maid = maid;
            this.delivered = delivered;
            this.fallback = fallback;
            this.needAddTools = false;
        }

        @Override
        public boolean shouldCacheTokenUsage() {
            return false;
        }

        @Override
        public void onSuccess(ResponseChat responseChat) {
            String prompt = sanitize(responseChat.getChatText());
            deliver(player, maid, prompt.isEmpty() ? fallback : prompt, delivered);
        }

        @Override
        public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
            CallResponseMod.LOGGER.debug("[生日] AI 提示词生成失败，改用内置提示词: {}",
                    throwable == null ? "unknown" : throwable.getMessage());
            deliver(player, maid, fallback, delivered);
        }
    }
}
