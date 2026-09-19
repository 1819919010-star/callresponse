package com.github.JumDa5he.callresponse.compat.npc;

import net.minecraft.util.RandomSource;

import java.util.Map;
import java.util.Optional;

/** AI 关闭时按事件 ID 与选项 ID 提供可扩展的固定语言键回复池。 */
public final class NpcEventFixedReplyProvider {
    private static final Map<String, Integer> POOL_SIZES = Map.ofEntries(
            pool("overwork", "rest"), pool("overwork", "encourage"), pool("overwork", "continue"),
            pool("nutrition_shortage", "meal", 2), pool("nutrition_shortage", "snack"), pool("nutrition_shortage", "ignore"),
            pool("food_variety", "change"), pool("food_variety", "favorite"), pool("food_variety", "endure"),
            pool("nightmare", "comfort"), pool("nightmare", "wait"), pool("nightmare", "wake"),
            pool("good_dream", "pet"), pool("good_dream", "watch"), pool("good_dream", "wake"),
            pool("long_time_no_interaction", "kind"), pool("long_time_no_interaction", "neutral"), pool("long_time_no_interaction", "cold"),
            pool("owner_hurt_nearby", "kind"), pool("owner_hurt_nearby", "neutral"), pool("owner_hurt_nearby", "cold"),
            pool("battle_praise", "praise"), pool("battle_praise", "caution"), pool("battle_praise", "normal"),
            pool("thunder_fear", "comfort"), pool("thunder_fear", "neutral"), pool("thunder_fear", "cruel"),
            pool("player_hurt_maid", "apologize"), pool("player_hurt_maid", "dismiss"), pool("player_hurt_maid", "cruel"),
            pool("mistake", "forgive"), pool("mistake", "warn"), pool("mistake", "scold"), pool("mistake", "punish"),
            pool("overfed", "rest"), pool("overfed", "tease"),
            pool("quiet_chat", "listen"), pool("quiet_chat", "busy"),
            pool("share_food", "accept"), pool("share_food", "later"), pool("share_food", "refuse"),
            pool("little_gift", "accept"), pool("little_gift", "return"),
            pool("nervous_question", "reassure"), pool("nervous_question", "avoid"),
            pool("self_worth", "treasure"), pool("self_worth", "partner"),
            pool("self_worth", "labor"), pool("self_worth", "nothing"),
            pool("revive_owner_killed", "apologize"), pool("revive_owner_killed", "welcome"),
            pool("revive_owner_killed", "caution"), pool("revive_owner_killed", "dismiss"),
            pool("revive_other", "welcome"), pool("revive_other", "ask"),
            pool("revive_other", "caution"), pool("revive_other", "work")
    );

    private NpcEventFixedReplyProvider() {
    }

    public static Optional<String> randomReplyKey(String eventId, String optionTextKey, RandomSource random) {
        String optionId = optionId(optionTextKey);
        int size = POOL_SIZES.getOrDefault(poolId(eventId, optionId), 0);
        if (size <= 0) return Optional.empty();
        int variant = size == 1 ? 1 : 1 + random.nextInt(size);
        return Optional.of("event.callresponse." + eventId + ".fixed_reply." + optionId + "." + variant);
    }

    private static Map.Entry<String, Integer> pool(String eventId, String optionId) {
        return pool(eventId, optionId, 1);
    }

    private static Map.Entry<String, Integer> pool(String eventId, String optionId, int size) {
        return Map.entry(poolId(eventId, optionId), size);
    }

    private static String poolId(String eventId, String optionId) {
        return eventId + "/" + optionId;
    }

    private static String optionId(String optionTextKey) {
        int separator = optionTextKey.lastIndexOf('.');
        return separator < 0 ? optionTextKey : optionTextKey.substring(separator + 1);
    }
}
