package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;

import java.util.regex.Pattern;

/** 统一清理广播、主动对话和谈话事件的模型可见输出。 */
public final class ChatTextSanitizer {
    private static final int MAX_LENGTH = 180;
    private static final Pattern INTERNAL_MARKER = Pattern.compile(
            "\\[\\[CALLRESPONSE_(?:TALK|BROADCAST):[^]]+]]");
    private static final Pattern LABEL_ONLY_LINE = Pattern.compile(
            "(?m)^\\s*(?:回复|台词|分析|旁白)\\s*[:：]?\\s*$");
    private static final Pattern LEADING_LABEL = Pattern.compile(
            "^(?:(?:回复|台词|分析|旁白)\\s*[:：]\\s*)+");
    private static final Pattern LEADING_ACTION = Pattern.compile(
            "^[（(](?=[^）)\\r\\n]{1,20}[）)])[^）)\\r\\n]{0,12}"
                    + "(?:笑|低头|抬头|点头|摇头|叹气|眨眼|歪头|皱眉|撇嘴|轻哼|沉默|后退|靠近|挥手|抱臂|摸头|捂脸)"
                    + "[^）)\\r\\n]{0,12}[）)]\\s*");
    private static final Pattern AI_DISCLOSURE_PAREN = Pattern.compile(
            "[（(][^）)\\r\\n]{0,20}(?:我是\\s*(?:AI|人工智能|模型)|作为\\s*(?:AI|人工智能|模型))[^）)\\r\\n]{0,40}[）)]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_AI_DISCLOSURE = Pattern.compile(
            "^(?:作为\\s*(?:AI|人工智能|模型)|我是\\s*(?:AI|人工智能|模型))[^，,。.!！?？]{0,50}[，,。.!！?？]\\s*",
            Pattern.CASE_INSENSITIVE);

    private ChatTextSanitizer() {
    }

    public static ResponseChat sanitize(ResponseChat response) {
        String chat = sanitize(response.getChatText());
        String tts = sanitize(response.getTtsText());
        if (tts.isBlank()) {
            tts = chat;
        }
        return new ResponseChat(chat, tts);
    }

    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = INTERNAL_MARKER.matcher(text).replaceAll("");
        cleaned = LABEL_ONLY_LINE.matcher(cleaned).replaceAll("");
        cleaned = cleaned.strip();

        // 前缀和句首短动作可能连续出现，因此循环剥离。
        String previous;
        do {
            previous = cleaned;
            cleaned = LEADING_LABEL.matcher(cleaned).replaceFirst("");
            cleaned = LEADING_ACTION.matcher(cleaned).replaceFirst("");
            cleaned = LEADING_AI_DISCLOSURE.matcher(cleaned).replaceFirst("");
            cleaned = cleaned.strip();
        } while (!cleaned.equals(previous));

        cleaned = AI_DISCLOSURE_PAREN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
        }
        return cleaned;
    }
}
