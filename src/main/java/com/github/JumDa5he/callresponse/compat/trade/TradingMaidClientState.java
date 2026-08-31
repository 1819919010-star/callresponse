package com.github.JumDa5he.callresponse.compat.trade;

import net.minecraft.client.Minecraft;

/** Short-lived client marker used to distinguish a wandering-trader merchant screen from a villager screen. */
public final class TradingMaidClientState {
    private static int traderId = -1;
    private static long validUntil;

    private TradingMaidClientState() {
    }

    public static void markTrader(int entityId) {
        traderId = entityId;
        validUntil = System.currentTimeMillis() + 5_000L;
    }

    public static int currentTraderId() {
        if (traderId < 0 || System.currentTimeMillis() > validUntil) {
            clear();
            return -1;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.level.getEntity(traderId) == null) {
            clear();
            return -1;
        }
        return traderId;
    }

    public static void clear() {
        traderId = -1;
        validUntil = 0L;
    }
}
