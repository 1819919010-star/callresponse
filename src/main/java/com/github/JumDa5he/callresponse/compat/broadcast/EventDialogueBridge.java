package com.github.JumDa5he.callresponse.compat.broadcast;

import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Short-lived guards around normal TLM chat, never a second LLM client or persistent queue. */
public final class EventDialogueBridge {
    public static final String MARKER = "[[CALLRESPONSE_EVENT:";
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> POTION_IN_FLIGHT = new ConcurrentHashMap<>();
    private static final long TIMEOUT_NANOS = 4_000_000_000L;
    private static final long POTION_TIMEOUT_NANOS = 120_000_000_000L;

    private static final class Pending {
        final EntityMaid maid;
        final ServerPlayer player;
        final Component fallback;
        final Predicate<EntityMaid> eligible;
        final Predicate<String> validText;
        final long expires;
        long waitingBubble;
        final boolean bubbleOnly;
        Pending(EntityMaid maid, ServerPlayer player, Component fallback,
                Predicate<EntityMaid> eligible, Predicate<String> validText, boolean bubbleOnly) {
            this.maid = maid; this.player = player; this.fallback = fallback;
            this.eligible = eligible; this.validText = validText;
            this.bubbleOnly = bubbleOnly;
            // Fixed-fallback events need a quick answer; spontaneous potion speech has no fallback.
            this.expires = System.nanoTime() + (bubbleOnly ? POTION_TIMEOUT_NANOS : TIMEOUT_NANOS);
        }
    }

    static UUID prepare(ServerPlayer player, EntityMaid maid, Component fallback,
                        Predicate<EntityMaid> eligible, Predicate<String> validText) {
        UUID id = UUID.randomUUID();
        PENDING.put(id, new Pending(maid, player, fallback, eligible, validText, false));
        return id;
    }

    /** A timed-out potion reply is discarded, but no second request starts until its callback ends. */
    static UUID preparePotion(ServerPlayer player, EntityMaid maid, Predicate<EntityMaid> eligible,
                              Predicate<String> validText) {
        if (POTION_IN_FLIGHT.containsValue(maid.getUUID()) || PENDING.values().stream().anyMatch(p -> p.maid == maid)) return null;
        UUID id = UUID.randomUUID();
        PENDING.put(id, new Pending(maid, player, null, eligible, validText, true));
        POTION_IN_FLIGHT.put(id, maid.getUUID());
        return id;
    }

    public static void forgetPotion(EntityMaid maid) {
        // Forget display eligibility, not an outstanding provider request. The same saved UUID
        // must not start another request merely because its Java entity object was reloaded.
        PENDING.entrySet().removeIf(e -> e.getValue().maid == maid && e.getValue().bubbleOnly);
    }

    /** Also recognizes callbacks created after display timeout/unload; the provider still owns this request. */
    public static boolean isPotionRequest(UUID id, EntityMaid maid) {
        return id != null && maid != null && maid.getUUID().equals(POTION_IN_FLIGHT.get(id));
    }

    static String mark(String prompt, UUID id) {
        return prompt + "\n\n" + MARKER + id + "]] 这是内部一次性事件标记，不要复述。";
    }

    public static UUID findRequest(List<LLMMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            String text = messages.get(i).message();
            int start = text.indexOf(MARKER);
            if (start < 0) continue;
            int end = text.indexOf("]]", start);
            if (end < 0) return null;
            try { return UUID.fromString(text.substring(start + MARKER.length(), end)); }
            catch (IllegalArgumentException malformed) { return null; }
        }
        return null;
    }

    public static void bind(UUID id, EntityMaid maid, long waitingBubble) {
        Pending pending = PENDING.get(id);
        if (pending != null && pending.maid == maid) pending.waitingBubble = waitingBubble;
        else if (maid.level() instanceof ServerLevel level)
            level.getServer().execute(() -> maid.getChatBubbleManager().removeChatBubble(waitingBubble));
    }

    public static void complete(UUID id, EntityMaid maid, ResponseChat response,
                                Consumer<ResponseChat> nativeDelivery) {
        POTION_IN_FLIGHT.remove(id, maid.getUUID());
        if (!(maid.level() instanceof ServerLevel level)) return;
        level.getServer().execute(() -> settle(id, maid, response, nativeDelivery));
    }

    public static void fail(UUID id, EntityMaid maid) {
        complete(id, maid, null, ignored -> {});
    }

    private static void settle(UUID id, EntityMaid maid, ResponseChat response,
                               Consumer<ResponseChat> nativeDelivery) {
        Pending pending = PENDING.get(id);
        if (pending == null || pending.maid != maid || !PENDING.remove(id, pending)) return;
        maid.getChatBubbleManager().removeChatBubble(pending.waitingBubble);
        if (!(maid.level() instanceof ServerLevel level) || level.getEntity(maid.getUUID()) != maid
                || pending.player.hasDisconnected() || pending.player.level() != level
                || !pending.player.isAlive() || !pending.eligible.test(maid)) return;
        String text = response == null ? "" : ChatTextSanitizer.sanitize(response.getChatText());
        // Use the validated spoken text for TTS as well: a separate TTS field must not say something else.
        if (System.nanoTime() <= pending.expires && !text.isBlank() && pending.validText.test(text)) {
            if (pending.bubbleOnly) maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type2(Component.literal(text)));
            else nativeDelivery.accept(new ResponseChat(text, text));
        } else if (pending.fallback != null) {
            maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type2(pending.fallback));
        }
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        long now = System.nanoTime();
        for (var entry : PENDING.entrySet()) {
            Pending pending = entry.getValue();
            if (pending.maid.level().getServer() == event.getServer() && now >= pending.expires)
                settle(entry.getKey(), pending.maid, null, ignored -> {});
        }
    }

    @SubscribeEvent
    public void onStop(ServerStoppedEvent event) { PENDING.clear(); POTION_IN_FLIGHT.clear(); }
}
