package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** 用内部标记把异步 LLM 回复对应回发起广播的玩家。 */
public final class BroadcastDialogueTracker {
    private static final String MARKER_PREFIX = "[[CALLRESPONSE_BROADCAST:";
    private static final String MARKER_SUFFIX = "]]";

    private BroadcastDialogueTracker() {
    }

    public static String mark(String message, UUID playerId) {
        return message + "\n\n" + MARKER_PREFIX + playerId + MARKER_SUFFIX
                + " 这是内部关联标记，不要复述或解释。";
    }

    @Nullable
    public static UUID findPlayerId(List<LLMMessage> messages) {
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

    public static String stripMarker(String message) {
        if (message == null) {
            return null;
        }
        int start = message.indexOf(MARKER_PREFIX);
        if (start < 0) {
            return message;
        }
        return message.substring(0, start).stripTrailing();
    }
}
