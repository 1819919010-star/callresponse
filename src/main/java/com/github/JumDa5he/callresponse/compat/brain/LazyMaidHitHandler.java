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

    @SubscribeEvent
    public void onMaidHurt(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide) return;

        // 饥饿/吃撑共用原版 starvation DamageSource，它绝不能被解释为主人教训。
        if (event.getSource().is(DamageTypes.STARVE)) return;

        // 复用保护破除链的来源追溯：近战、投射物及能暴露 owner/shooter/caster 的枪械
        // 都必须最终追溯到这只女仆自己的当前主人，其他任何伤害都不触发受击反馈。
        if (!OwnerDamageSource.isCurrentOwnerSource(maid, event.getSource())) return;

        if (!isLazyMode(maid)) return;

        // Mixin 会在伤害管线开始前保留旧有的立即逃跑反馈；原始 DamageSource 进入
        // LivingDamageEvent 后不要在同一 tick 再触发一次对话和情感变化。
        if (maid.getPersistentData().contains(KEY_ESCAPE_TICK)
                && maid.getPersistentData().getLong(KEY_ESCAPE_TICK) == maid.level().getGameTime()) return;

        ServerPlayer sp = OwnerDamageSource.findServerPlayer(maid, event.getSource());
        if (sp != null) {
            triggerEscape(maid, sp);
        }
    }

    public static boolean isLazyMode(EntityMaid maid) {
        return "callresponse:lazy".equals(maid.getTask().getUid().toString());
    }

    public static void triggerEscape(EntityMaid maid, ServerPlayer player) {
        maid.getPersistentData().putLong(KEY_ESCAPE_TICK, maid.level().getGameTime());
        SoundEvent[] hurtSounds = { InitSounds.MAID_HURT.get() };
        maid.playSound(hurtSounds[RANDOM.nextInt(hurtSounds.length)], 1f, 1f);
        EmotionData.addFear(maid, player.getUUID(), Math.round(0.5f));
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
