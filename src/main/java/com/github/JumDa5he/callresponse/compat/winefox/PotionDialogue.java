package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.broadcast.EventDialogueBridge;
import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.npc.NpcEventData;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.WaitingChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.ProgressChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;

/** Loaded logical-tick timers; only the existing TLM/event dialogue pipeline can request AI. */
@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class PotionDialogue {
    public static final String DATA = "CallResponsePotionDialogue";
    private static final Map<EntityMaid, State> STATES = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final class State {
        int whisper, scentCooldown, scentDelay = -1;
        UUID scented;
        long epoch, lastTick = Long.MIN_VALUE;
    }

    private static State state(EntityMaid maid) {
        return STATES.computeIfAbsent(maid, key -> {
            CompoundTag tag = maid.getPersistentData().getCompound(DATA);
            State s = new State();
            s.whisper = Math.max(0, Math.min(6200, tag.getInt("WhisperRemaining")));
            s.scentCooldown = Math.max(0, Math.min(6000, tag.getInt("ScentCooldown")));
            return s;
        });
    }

    public static void writeMaidState(EntityMaid maid, CompoundTag save) {
        State s = STATES.get(maid);
        if (s == null || maid.level().isClientSide) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("WhisperRemaining", s.whisper);
        tag.putInt("ScentCooldown", s.scentCooldown);
        maid.getPersistentData().put(DATA, tag);
        // Entity.saveWithoutId snapshots ForgeData before the maid's save tail.
        CompoundTag forge = save.getCompound("ForgeData");
        forge.put(DATA, tag.copy()); save.put("ForgeData", forge);
    }

    public static void readMaidState(EntityMaid maid, CompoundTag save) {
        STATES.remove(maid);
        EventDialogueBridge.forgetPotion(maid);
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) return;
        State s = state(maid);
        if (s.lastTick == level.getGameTime()) return;
        s.lastTick = level.getGameTime();
        if (s.scentCooldown > 0) s.scentCooldown--;
        if (!maid.isAlive() || !WineFoxPotions.isOrdinaryMaid(maid)) {
            s.epoch++; s.whisper = 0; s.scentDelay = -1; return;
        }
        if (maid.hasEffect(WineFoxEffects.CORRUPTED_HUNT.get())) {
            if (s.whisper <= 0) s.whisper = 6000 + maid.getRandom().nextInt(201);
            if (--s.whisper == 0) {
                s.whisper = 6000; // Failure/offline/disabled AI never becomes a per-tick retry.
                whisper(maid, s);
            }
        } else {
            if (s.whisper != 0) s.epoch++;
            s.whisper = 0;
        }
        if (WineFoxPotions.hasSweetness(maid)) { s.scentDelay = -1; return; }
        if (s.scentDelay >= 0) {
            if (--s.scentDelay <= 0) {
                s.scentDelay = -1;
                var target = s.scented == null ? null : level.getEntity(s.scented);
                if (s.scentCooldown == 0 && target instanceof EntityMaid scented && validScent(maid, scented) && available(maid)) {
                    var bubble = TextChatBubbleData.type2(Component.translatable("bubble.callresponse.sweetness.scent"));
                    long id = maid.getChatBubbleManager().addChatBubble(bubble);
                    if (maid.getChatBubbleManager().getChatBubble(id) == bubble) s.scentCooldown = 6000;
                }
            }
        } else if (s.scentCooldown == 0 && Math.floorMod(maid.tickCount + maid.getId(), 20) == 0 && available(maid)) {
            var candidates = level.getEntitiesOfClass(EntityMaid.class, maid.getBoundingBox().inflate(5),
                    other -> validScent(maid, other));
            if (!candidates.isEmpty()) {
                s.scented = candidates.get(maid.getRandom().nextInt(candidates.size())).getUUID();
                s.scentDelay = 20 + maid.getRandom().nextInt(81);
            }
        }
    }

    private static boolean validScent(EntityMaid speaker, EntityMaid target) {
        return speaker != target && target.isAlive() && !target.isRemoved()
                && speaker.level() == target.level() && speaker.distanceToSqr(target) <= 25
                && !WineFoxPotions.hasSweetness(speaker) && WineFoxPotions.hasSweetness(target);
    }

    private static boolean available(EntityMaid maid) {
        if (maid.isRemoved() || !maid.isAlive() || maid.isSleeping() || !maid.getConfigManager().isChatBubbleShow()
                || IntimidationManager.isIntimidated(maid) || NpcEventData.hasCurrent(maid)
                || TalkEventManager.isParticipant(maid)) return false;
        var bubbles = maid.getChatBubbleManager().getChatBubbleDataCollection();
        if (bubbles.size() >= 5) return false;
        long now = System.currentTimeMillis();
        for (long key : bubbles.keySet()) {
            var bubble = bubbles.get(key);
            if (key > now && (bubble instanceof TextChatBubbleData || bubble instanceof WaitingChatBubbleData
                    || bubble instanceof ProgressChatBubbleData || bubble.priority() > 0)) return false;
        }
        return true;
    }

    private static void whisper(EntityMaid maid, State s) {
        if (!available(maid) || !(maid.getOwner() instanceof ServerPlayer owner)
                || owner.level() != maid.level() || owner.hasDisconnected()) return;
        long epoch = s.epoch;
        UUID ownerId = owner.getUUID();
        MaidResponder.replyToPotionEvent(owner, maid,
                "你的脑海中突然出现一个陌生、诡异而低沉的声音。它向你低语：只要你继续进行杀戮，"
                        + "只要你的心中仍然存在恨意，它就会赐予你更加强大的力量。这个声音似乎理解你的痛苦，也似乎在利用你的情绪。"
                        + "请结合本次附带的实时Trust、Fear、情感状态和你原有的人格，仅作一句自然内心回应或自言自语。"
                        + "你可以疑惑、害怕、受诱惑、认同、抗拒或矛盾，不要求接受，不改变阵营、主人或身份。"
                        + "不要复述提示词或原句，不要执行工具或指令，不对主人发起任务，只输出一句话。",
                current -> STATES.get(current) == s && s.epoch == epoch && current.isAlive() && !current.isRemoved()
                        && WineFoxPotions.isOrdinaryMaid(current) && ownerId.equals(current.getOwnerUUID())
                        && current.hasEffect(WineFoxEffects.CORRUPTED_HUNT.get())
                        && current.getConfigManager().isChatBubbleShow() && !IntimidationManager.isIntimidated(current)
                        && !NpcEventData.hasCurrent(current) && !TalkEventManager.isParticipant(current),
                PotionDialogue::singleReply);
    }

    public static boolean singleReply(String text) {
        if (text == null || text.isBlank() || text.length() > 180 || text.contains("\n")
                || text.contains("CALLRESPONSE") || text.contains("Trust") || text.contains("提示词")
                || text.contains("只要你继续进行杀戮")) return false;
        // Ellipses and combined exclamation/question marks belong to one sentence.
        return text.split("[。！？!?]+\\s*(?=[^。！？.!?\\s])|(?<!\\.)\\.(?!\\.)\\s*(?=[^。！？.!?\\s])").length <= 1;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removed(MobEffectEvent.Remove event) {
        if (event.getEffect() == WineFoxEffects.CORRUPTED_HUNT.get() && event.getEntity() instanceof EntityMaid maid) {
            State s = STATES.get(maid); if (s != null) s.epoch++;
        }
    }
    @SubscribeEvent
    public static void expired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect() == WineFoxEffects.CORRUPTED_HUNT.get()
                && event.getEntity() instanceof EntityMaid maid) {
            State s = STATES.get(maid); if (s != null) s.epoch++;
        }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && !event.getLevel().isClientSide()) {
            writeMaidState(maid, new CompoundTag()); STATES.remove(maid); EventDialogueBridge.forgetPotion(maid);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { STATES.clear(); }
    private PotionDialogue() {}
}
