package com.github.JumDa5he.callresponse.compat.broadcast.tool;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;


public final class OneShotToolCall {
    private static final Map<LLMCallback, Set<String>> USED_TOOLS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private OneShotToolCall() {
    }

    public static boolean claim(LLMCallback callback, String toolName) {
        synchronized (USED_TOOLS) {
            return USED_TOOLS.computeIfAbsent(callback, ignored -> new HashSet<>()).add(toolName);
        }
    }

    public static LLMCallback finish(LLMCallback callback, String toolCallId, String toolName, String result) {
        callback.needAddTools = false;
        return callback.addToolResult(result + "\n本次对话的工具操作已经完成，请不要再次调用任何工具，直接用自然语言回复玩家。", toolCallId);
    }

    public static LLMCallback alreadyUsed(LLMCallback callback, String toolCallId, String toolName) {
        callback.needAddTools = false;
        return callback.addToolResult("本次对话中工具“" + toolName + "”已经执行过一次，禁止重复调用；请直接用自然语言回复玩家。", toolCallId);
    }
}
