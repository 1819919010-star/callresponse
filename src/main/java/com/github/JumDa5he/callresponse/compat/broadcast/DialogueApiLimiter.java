package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.JumDa5he.callresponse.config.BroadcastConfig;

import java.util.ArrayDeque;
import java.util.Deque;

/** 只限制《呼应》自己发起的 AI 请求，不干涉 TLM 本体的普通单体聊天。 */
public final class DialogueApiLimiter {
    private static final long WINDOW_MILLIS = 60_000L;
    private static final Deque<Long> recentCalls = new ArrayDeque<>();

    private DialogueApiLimiter() {
    }

    public static synchronized boolean tryAcquire() {
        int limit = BroadcastConfig.API_CALLS_PER_MINUTE.get();
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        while (!recentCalls.isEmpty() && now - recentCalls.peekFirst() >= WINDOW_MILLIS) {
            recentCalls.removeFirst();
        }
        if (recentCalls.size() >= limit) {
            return false;
        }
        recentCalls.addLast(now);
        return true;
    }
}
