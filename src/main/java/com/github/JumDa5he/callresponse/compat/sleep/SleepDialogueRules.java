package com.github.JumDa5he.callresponse.compat.sleep;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import net.minecraft.util.RandomSource;

/** Time and probability rules only; never changes TLM's schedule. */
final class SleepDialogueRules {
    private SleepDialogueRules() {}

    static boolean supported(MaidSchedule schedule) {
        return schedule == MaidSchedule.DAY || schedule == MaidSchedule.NIGHT;
    }

    static boolean chatter(MaidSchedule schedule, long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        return schedule == MaidSchedule.DAY ? time >= 16000 && time < 19000
                : schedule == MaidSchedule.NIGHT && time < 3000;
    }

    static int nightmarePercent(int trust, int fear) {
        int difference = fear - trust;
        if (difference < 0) return 10;
        if (difference <= 25) return 30;
        if (difference <= 50) return 50;
        if (difference <= 75) return 70;
        return 90;
    }

    static int delay(RandomSource random, boolean chatter) {
        return chatter ? 200 + random.nextInt(201) : 500 + random.nextInt(401);
    }

    static int line(RandomSource random, int size, int previous) {
        if (previous < 0 || previous >= size) return random.nextInt(size);
        int next = random.nextInt(size - 1);
        return next >= previous ? next + 1 : next;
    }
}
