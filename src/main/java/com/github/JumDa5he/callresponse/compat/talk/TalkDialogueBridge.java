package com.github.JumDa5he.callresponse.compat.talk;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.ChatClientInfo;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Marks one normal TLM chat request as a talk-event request and receives the
 * final text from the callback mixin. TLM still owns persona, provider, bubble
 * and TTS handling.
 */
public final class TalkDialogueBridge {
    public static final String MARKER_PREFIX = "[[CALLRESPONSE_TALK:";
    private static final String MARKER_SUFFIX = "]]";
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private TalkDialogueBridge() {
    }

    public static UUID request(EntityMaid maid, ServerPlayer owner, String prompt,
                               Consumer<String> success, Runnable failure) {
        UUID id = UUID.randomUUID();
        PENDING.put(id, new Pending(maid.getUUID(), success, failure));
        String markedPrompt = prompt + "\n\n" + MARKER_PREFIX + id + MARKER_SUFFIX
                + " 这是内部事件标记，绝对不要复述或解释它。";
        ChatClientInfo clientInfo = new ChatClientInfo(
                owner.getLanguage(), maid.getName().getString(), Collections.emptyList());
        try {
            maid.getAiChatManager().chat(markedPrompt, clientInfo, owner);
        } catch (Throwable throwable) {
            PENDING.remove(id);
            CallResponseMod.LOGGER.error("谈话事件调用女仆 AI 失败", throwable);
            failure.run();
        }
        return id;
    }

    @Nullable
    public static UUID findRequestId(List<LLMMessage> messages) {
        for (LLMMessage message : messages) {
            String text = message.message();
            int start = text.indexOf(MARKER_PREFIX);
            if (start < 0) {
                continue;
            }
            start += MARKER_PREFIX.length();
            int end = text.indexOf(MARKER_SUFFIX, start);
            if (end > start) {
                try {
                    return UUID.fromString(text.substring(start, end));
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public static boolean isTalkPrompt(String message) {
        return message != null && message.contains(MARKER_PREFIX);
    }

    public static boolean isPending(UUID id) {
        return id != null && PENDING.containsKey(id);
    }

    public static void complete(UUID id, EntityMaid maid, String text) {
        Pending pending = PENDING.remove(id);
        if (pending == null || !pending.maidId.equals(maid.getUUID())) {
            return;
        }
        runOnServer(maid, () -> pending.success.accept(text));
    }

    public static void fail(UUID id, EntityMaid maid) {
        Pending pending = PENDING.remove(id);
        if (pending == null || !pending.maidId.equals(maid.getUUID())) {
            return;
        }
        runOnServer(maid, pending.failure);
    }

    public static void cancel(UUID id) {
        Pending pending = PENDING.remove(id);
        if (pending != null) {
            pending.failure.run();
        }
    }

    private static void runOnServer(EntityMaid maid, Runnable action) {
        if (maid.level() instanceof ServerLevel level) {
            level.getServer().submit(action);
        }
    }

    private record Pending(UUID maidId, Consumer<String> success, Runnable failure) {
    }
}
