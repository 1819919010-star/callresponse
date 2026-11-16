package com.github.JumDa5he.callresponse.compat.gift.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 客户端提示用的键位名（只在物理客户端加载）。
 */
public final class GiftClientHints {
    private GiftClientHints() {
    }

    /** 「使用/放置」键的当前显示名，比如「鼠标左键」。 */
    public static String useKeyName() {
        try {
            Component key = Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage();
            return key.getString();
        } catch (Exception ignored) {
            return "右键";
        }
    }
}
