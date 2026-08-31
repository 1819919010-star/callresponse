package com.github.JumDa5he.callresponse.compat.brain;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.Collections;
import java.util.Random;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import com.github.tartaricacid.touhoulittlemaid.util.ItemsUtil;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import com.github.JumDa5he.callresponse.init.InitAttachTypes;
import net.minecraft.core.component.DataComponents;

public class LazyMaidHitHandler {
    private static final Random RANDOM = new Random();
    static final String KEY_ESCAPE_TICK = "LazyEscapeTick";

    @SubscribeEvent
    public void onMaidHurt(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide()) return;

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;
        if (maid.getOwner() == null || !player.getUUID().equals((maid.getOwner() == null ? null : maid.getOwner().getUUID()))) return;

        if (!isLazyMode(maid)) return;

        if (maid.getOwner() instanceof ServerPlayer sp) {
            triggerEscape(maid, sp);
        }
    }

    public static boolean isLazyMode(EntityMaid maid) {
        return "callresponse:lazy".equals(maid.getTask().getUid().toString());
    }

    public static void triggerEscape(EntityMaid maid, ServerPlayer player) {
        dropFoodFromHands(maid);
        InitAttachTypes.persistentData(maid).putLong(KEY_ESCAPE_TICK, maid.level().getGameTime());
        SoundEvent[] hurtSounds = { InitSounds.MAID_HURT.get() };
        maid.playSound(hurtSounds[RANDOM.nextInt(hurtSounds.length)], 1f, 1f);
        EmotionData.addFear(maid, player.getUUID(), Math.round(0.5f));
        String tendencyDesc = EmotionData.getTendencyPromptSuffix(maid, player.getUUID());
        String prompt = "你突然被主人打了一下，吓得赶紧跑开。你或委屈或生气或害怕，不明白主人为什么打你。根据你目前的情感状态和对主人的态度" + tendencyDesc;
        MaidResponder.processBroadcast(player, Collections.singletonList(maid), prompt, false);
    }

    static void dropFoodFromHands(EntityMaid maid) {
        ItemStack mainHand = maid.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.get(DataComponents.FOOD) != null) {
            spawnFoodDrop(maid, mainHand.copy());
            maid.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        ItemStack offHand = maid.getOffhandItem();
        if (!offHand.isEmpty() && offHand.get(DataComponents.FOOD) != null) {
            spawnFoodDrop(maid, offHand.copy());
            maid.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }
    }

    private static void spawnFoodDrop(EntityMaid maid, ItemStack stack) {
        double angle = RANDOM.nextDouble() * 2 * Math.PI;
        double speed = 0.2 + RANDOM.nextDouble() * 0.2;
        double vx = Math.cos(angle) * speed;
        double vz = Math.sin(angle) * speed;
        double vy = 0.3 + RANDOM.nextDouble() * 0.2;
        ItemEntity drop = new ItemEntity(maid.level(),
                maid.getX(), maid.getY() + 3.5, maid.getZ(),
                stack, vx, vy, vz);
        maid.level().addFreshEntity(drop);
    }

    static boolean checkEscape(EntityMaid maid) {
        long escapeTick = InitAttachTypes.persistentData(maid).getLong(KEY_ESCAPE_TICK).orElse(0L);
        if (escapeTick <= 0) return false;
        long now = maid.level().getGameTime();
        if (now - escapeTick > 5) {
            InitAttachTypes.persistentData(maid).remove(KEY_ESCAPE_TICK);
            return false;
        }
        return true;
    }
}
