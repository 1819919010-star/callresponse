package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.broadcast.MaidResponder;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.npc.NpcEventData;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidData;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampData;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.AIConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import net.minecraft.advancements.FrameType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Owner achievement speech only: no movement, Brain, rewards or relationship mutations. */
public final class OwnerAdvancementResponse {
    private static final int COOLDOWN_TICKS = 1200;
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, Map<ResourceLocation, Long>> SEEN = new HashMap<>();
    private static final List<Delayed> DELAYED = new ArrayList<>();
    private record Context(UUID id, ServerPlayer owner, Component title, Component description, FrameType frame) {}
    private record Delayed(Context context, EntityMaid maid, long due) {}

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var advancement = event.getAdvancement();
        var display = advancement.getDisplay();
        if (display == null || display.getFrame() == FrameType.TASK) return;
        var progress = player.getAdvancements().getOrStartProgress(advancement);
        if (!progress.isDone()) return;
        long completedAt = 0;
        for (String criterion : progress.getCompletedCriteria()) {
            var obtained = progress.getCriterion(criterion).getObtained();
            if (obtained != null) completedAt = Math.max(completedAt, obtained.getTime());
        }
        var seen = SEEN.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
        if (Objects.equals(seen.put(advancement.getId(), completedAt), completedAt)) return;
        Context context = new Context(UUID.randomUUID(), player, display.getTitle().copy(),
                display.getDescription().copy(), display.getFrame());
        respond(context);
    }

    private static void respond(Context context) {
        ServerPlayer player = context.owner;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        List<EntityMaid> candidates = level.getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(16), maid -> eligible(maid, player)
                        && now >= COOLDOWN.getOrDefault(maid.getUUID(), 0L));
        List<EntityMaid> chosen = choose(candidates, player.getRandom());
        List<Integer> delays = new ArrayList<>();
        for (int delay = 10; delay <= 30; delay++) delays.add(delay);
        for (int i = 0; i < chosen.size(); i++) {
            EntityMaid maid = chosen.get(i);
            COOLDOWN.put(maid.getUUID(), now + COOLDOWN_TICKS);
            if (i == 0) speak(context, maid);
            else DELAYED.add(new Delayed(context, maid,
                    now + delays.remove(player.getRandom().nextInt(delays.size()))));
        }
    }

    static List<EntityMaid> choose(List<EntityMaid> candidates, RandomSource random) {
        List<EntityMaid> shuffled = new ArrayList<>(candidates);
        for (int i = shuffled.size() - 1; i > 0; i--) Collections.swap(shuffled, i, random.nextInt(i + 1));
        if (shuffled.isEmpty()) return shuffled;
        return new ArrayList<>(shuffled.subList(0, 1 + random.nextInt(Math.min(3, shuffled.size()))));
    }

    static boolean eligible(EntityMaid maid, ServerPlayer player) {
        return !player.hasDisconnected() && player.isAlive() && !maid.isRemoved() && maid.isAlive()
                && maid.deathTime == 0 && maid.isTame() && player.getUUID().equals(maid.getOwnerUUID())
                && maid.level() == player.level() && maid.distanceToSqr(player) <= 256
                && !BetrayalOutpostMaidData.isOutpostMaid(maid) && !EmotionBetrayalManager.isBetraying(maid)
                && !WanderingCampData.active(maid) && !WanderingMaidData.isSpecial(maid)
                && !TradingMaidData.isTrading(maid) && !IntimidationManager.isIntimidated(maid)
                && !TalkEventManager.isParticipant(maid) && !NpcEventData.hasCurrent(maid)
                && !maid.isSleeping() && !maid.isNoAi() && maid.getTarget() == null
                && !maid.getPersistentData().getCompound(MaidMovementControl.ROOT_KEY)
                    .getCompound("Reasons").contains(MaidMovementControl.Reason.CAGE.name());
    }

    private static void speak(Context context, EntityMaid maid) {
        if (!eligible(maid, context.owner) || !(maid.level() instanceof ServerLevel level)
                || level.getEntity(maid.getUUID()) != maid) return;
        var tendency = EmotionData.getTendency(maid, context.owner);
        Component fallback = fixed(tendency, context.title, maid.getRandom());
        if (!BroadcastConfig.NPC_EVENT_AI_REPLY_ENABLED.get() || !AIConfig.LLM_ENABLED.get()) {
            maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type2(fallback));
            return;
        }
        String title = context.title.getString();
        String prompt = "你的主人「" + context.owner.getName().getString() + "」刚完成重要成就「" + title
                + "」。这是主人的成就，不是你的成就。等级：" + context.frame + "；描述：" + context.description.getString()
                + "。当前七档情感为" + tendency + "。结合你原有人格、主人称呼和本轮情感上下文，自然回应主人1～2句话。"
                + "每句必须自然提到成就名称「" + title + "」，不要说自己取得成就，不解释机制，不说自己是AI，不改变玩法或数值。"
                + "本次是正面成就场景：若当前为纠结/高信任高Fear（也可能忠诚），偏向对主人实力的敬畏、认可、尊敬和克制崇拜；"
                + "敬畏不是敌意或逃避，不要主动说害怕主人、不敢靠近或不知道是否相信主人。";
        MaidResponder.replyToEvent(context.owner, maid, prompt, fallback,
                value -> eligible(value, context.owner), text -> mentionsTitle(text, title));
    }

    static Component fixed(EmotionData.EmotionTendency tendency, Component title, RandomSource random) {
        int size = tendency == EmotionData.EmotionTendency.CONFLICTED ? 5 : 3;
        return Component.translatable("bubble.callresponse.advancement." + tendency.name().toLowerCase(Locale.ROOT)
                + "." + (1 + random.nextInt(size)), title);
    }

    static boolean mentionsTitle(String text, String title) {
        if (text == null || title.isBlank()) return false;
        // Sentence separators inside the advancement title must not split that title itself.
        String marked = text.replace(title, "\u0001");
        for (String sentence : marked.split("[。！？.!?\\n]+")) {
            if (!sentence.isBlank() && !sentence.contains("\u0001")) return false;
        }
        return marked.contains("\u0001");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        List<Delayed> due = new ArrayList<>();
        DELAYED.removeIf(pending -> {
            if (!eligible(pending.maid, pending.context.owner)) return true;
            if (pending.context.owner.level().getGameTime() < pending.due) return false;
            due.add(pending); return true;
        });
        for (Delayed pending : due) speak(pending.context, pending.maid);
        if (event.getServer().getTickCount() % 20 == 0)
            COOLDOWN.values().removeIf(until -> until <= event.getServer().overworld().getGameTime());
    }

    @SubscribeEvent
    public void onStop(ServerStoppedEvent event) { DELAYED.clear(); COOLDOWN.clear(); SEEN.clear(); }
}
