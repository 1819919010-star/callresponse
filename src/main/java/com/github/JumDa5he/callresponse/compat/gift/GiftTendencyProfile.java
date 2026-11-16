package com.github.JumDa5he.callresponse.compat.gift;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import net.minecraft.util.RandomSource;

import java.util.EnumMap;
import java.util.Map;

/**
 * 情感倾向 → 礼物种类权重。
 * <p>
 * 设计取向：好感侧最多出「恶作剧」，真陷阱只出现在畏惧 / 恐惧 / 矛盾 / 背叛方向；
 * 羁绊（BOND）保留少量撒娇式恶作剧，但不出伤人陷阱。
 */
public final class GiftTendencyProfile {
    private static final Map<EmotionData.EmotionTendency, Map<GiftKind, Integer>> TABLE =
            new EnumMap<>(EmotionData.EmotionTendency.class);

    static {
        TABLE.put(EmotionData.EmotionTendency.BOND, weights(
                GiftKind.SWEET, 25,
                GiftKind.FLOWER, 15,
                GiftKind.TRINKET, 15,
                GiftKind.HANDMADE, 20,
                GiftKind.TREASURE, 15,
                GiftKind.PRANK, 10));

        TABLE.put(EmotionData.EmotionTendency.FRIENDLY, weights(
                GiftKind.SWEET, 30,
                GiftKind.FLOWER, 18,
                GiftKind.TRINKET, 22,
                GiftKind.HANDMADE, 12,
                GiftKind.TREASURE, 10,
                GiftKind.PRANK, 8));

        TABLE.put(EmotionData.EmotionTendency.NEUTRAL, weights(
                GiftKind.SWEET, 22,
                GiftKind.FLOWER, 12,
                GiftKind.TRINKET, 28,
                GiftKind.HANDMADE, 8,
                GiftKind.STALE, 15,
                GiftKind.PRANK, 15));

        TABLE.put(EmotionData.EmotionTendency.STRANGER, weights(
                GiftKind.SWEET, 8,
                GiftKind.FLOWER, 4,
                GiftKind.TRINKET, 15,
                GiftKind.STALE, 63,
                GiftKind.PRANK, 10));

        TABLE.put(EmotionData.EmotionTendency.FEARFUL, weights(
                GiftKind.SWEET, 8,
                GiftKind.FLOWER, 4,
                GiftKind.TRINKET, 12,
                GiftKind.STALE, 36,
                GiftKind.PRANK, 20,
                GiftKind.SPAWN, 10,
                GiftKind.BLAST, 10));

        TABLE.put(EmotionData.EmotionTendency.TERRIFIED, weights(
                GiftKind.SWEET, 4,
                GiftKind.TRINKET, 6,
                GiftKind.STALE, 30,
                GiftKind.PRANK, 10,
                GiftKind.SPAWN, 25,
                GiftKind.TNT, 10,
                GiftKind.BLAST, 15));

        TABLE.put(EmotionData.EmotionTendency.CONFLICTED, weights(
                GiftKind.SWEET, 16,
                GiftKind.FLOWER, 8,
                GiftKind.TRINKET, 16,
                GiftKind.HANDMADE, 4,
                GiftKind.STALE, 16,
                GiftKind.PRANK, 20,
                GiftKind.SPAWN, 8,
                GiftKind.TNT, 4,
                GiftKind.BLAST, 8));
    }

    private GiftTendencyProfile() {
    }

    public static GiftKind roll(EmotionData.EmotionTendency tendency, EmotionData.EmotionValues values, RandomSource random) {
        Map<GiftKind, Integer> weights = new EnumMap<>(TABLE.getOrDefault(tendency,
                TABLE.get(EmotionData.EmotionTendency.NEUTRAL)));

        // 溺爱：更愿意把珍藏和亲手做的东西拿出来
        if (values.trust() >= 90 && values.fear() <= 10) {
            weights.merge(GiftKind.TREASURE, 20, Integer::sum);
            weights.merge(GiftKind.HANDMADE, 10, Integer::sum);
        }
        // 背叛：真陷阱大幅加码
        if (values.trust() <= 10 && values.fear() >= 90) {
            weights.merge(GiftKind.SPAWN, 10, Integer::sum);
            weights.merge(GiftKind.TNT, 8, Integer::sum);
            weights.merge(GiftKind.BLAST, 8, Integer::sum);
            weights.merge(GiftKind.GRUDGE, 6, Integer::sum);
        }

        int total = 0;
        for (int weight : weights.values()) {
            total += Math.max(0, weight);
        }
        if (total <= 0) {
            return GiftKind.STALE;
        }

        int target = random.nextInt(total);
        for (Map.Entry<GiftKind, Integer> entry : weights.entrySet()) {
            int weight = Math.max(0, entry.getValue());
            if (target < weight) {
                return entry.getKey();
            }
            target -= weight;
        }
        return GiftKind.STALE;
    }

    private static Map<GiftKind, Integer> weights(Object... pairs) {
        Map<GiftKind, Integer> map = new EnumMap<>(GiftKind.class);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((GiftKind) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }
}
