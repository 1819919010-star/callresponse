package com.github.tartaricacid.callresponse.compat.hunger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HungerClientCache {
    private static final Map<UUID, Integer> CACHE = new ConcurrentHashMap<>();

    public static void setHunger(UUID maidUUID, int hunger) {
        CACHE.put(maidUUID, hunger);
    }

    public static int getHunger(UUID maidUUID) {
        return CACHE.getOrDefault(maidUUID, -1);
    }

    public static void remove(UUID maidUUID) {
        CACHE.remove(maidUUID);
    }
}