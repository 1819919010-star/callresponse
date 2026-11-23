package com.github.JumDa5he.callresponse.compat.brain;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageSource;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

import java.util.Collections;
import java.util.Random;

public class LazyMaidHitHandler {
    private static final Random RANDOM = new Random();
    static final String KEY_ESCAPE_TICK = "LazyEscapeTick";

    public static boolean isLazyMode(EntityMaid maid) {
        return "callresponse:lazy".equals(maid.getTask().getUid().toString());
    }

    public static void triggerEscape(EntityMaid maid, ServerPlayer player) {
        maid.getPersistentData().putLong(KEY_ESCAPE_TICK, maid.level().getGameTime());
        SoundEvent[] hurtSounds = { InitSounds.MAID_HURT.get() };
        maid.playSound(hurtSounds[RANDOM.nextInt(hurtSounds.length)], 1f, 1f);
        // The common final-damage settlement has already included this fear subitem.
        String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, player.getUUID());
        String prompt = "你突然被主人打了一下，吓得赶紧跑开。你或委屈或生气或害怕，不明白主人为什么打你。根据你目前的情感状态和对主人的态度" + tendencyDesc;
        MaidResponder.processBroadcast(player, Collections.singletonList(maid), prompt, false);
    }

    static boolean checkEscape(EntityMaid maid) {
        long escapeTick = maid.getPersistentData().getLong(KEY_ESCAPE_TICK);
        if (escapeTick <= 0) return false;
        long now = maid.level().getGameTime();
        if (now - escapeTick > 5) {
            maid.getPersistentData().remove(KEY_ESCAPE_TICK);
            return false;
        }
        return true;
    }
}
