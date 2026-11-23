package com.github.JumDa5he.callresponse.compat.sleep;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.intimidation.IntimidationManager;
import com.github.JumDa5he.callresponse.compat.npc.NpcEventData;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.JumDa5he.callresponse.compat.task.LazyMaidTask;
import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidData;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampData;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.WaitingChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.ProgressChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

/** Observes real sleep and emits native bubbles. No AI, movement or sleep mutations. */
public final class SleepDialogueManager {
    static final String QUOTA = "callresponse:sleep_dialogue_relief";
    static final int LOCAL_GAP = 40;
    private static final Map<EntityMaid, SleepState> SLEEPERS = new IdentityHashMap<>();
    private static final Map<UUID, OwnerMotion> MOTION = new HashMap<>();

    static final class SleepState {
        final EntityMaid maid;
        final UUID owner;
        final ServerLevel level;
        final MaidSchedule schedule;
        boolean chatter;
        long nextTalk = -1, lastBubble = Long.MIN_VALUE;
        boolean postponed;
        String lastLine;
        long bubbleKey = -1;
        IChatBubbleData bubble;
        Group group;
        SleepState(EntityMaid maid) {
            this.maid = maid; owner = maid.getOwnerUUID(); level = (ServerLevel) maid.level();
            schedule = maid.getSchedule(); chatter = SleepDialogueRules.chatter(schedule, level.getDayTime());
        }
    }

    static final class Group {
        Set<UUID> members = new HashSet<>();
        boolean discovered;
        long outsideSince = -1;
    }

    /** Sample only owners of tracked sleepers; orientation never participates in this test. */
    static final class OwnerMotion {
        boolean sneaking, anchored;
        Vec3 anchor;
        Vec3 previousPosition;
        ServerLevel level;
        void sample(ServerPlayer owner) {
            boolean nowSneaking = owner.isShiftKeyDown();
            if (nowSneaking) {
                anchored = false;
            } else if (sneaking && level == owner.serverLevel()) {
                anchor = owner.position();
                anchored = previousPosition != null && previousPosition.distanceToSqr(anchor) <= .12 * .12;
            }
            if (anchored && (level != owner.serverLevel() || owner.position().distanceToSqr(anchor) > .12 * .12
                    || owner.isSprinting() || !owner.onGround()
                    || owner.getDeltaMovement().lengthSqr() > .01)) anchored = false;
            sneaking = nowSneaking; level = owner.serverLevel(); previousPosition = owner.position();
        }
        boolean hidden(ServerPlayer owner, List<SleepState> members) {
            return owner.isShiftKeyDown() || (anchored && level == owner.serverLevel()
                    && members.stream().anyMatch(s -> s.maid.position().distanceToSqr(anchor) <= 25));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel)) return;
        // O(1) observation catches waking, including damage, without any world query.
        observe(maid, event.isCanceled());
    }

    static void observe(EntityMaid maid, boolean canceled) {
        SleepState state = SLEEPERS.get(maid);
        if (!maid.isSleeping()) {
            if (state != null) {
                SLEEPERS.remove(maid); // consume transition before optional speech
                if (!canceled && state.owner.equals(maid.getOwnerUUID()) && state.level == maid.level()
                        && eligible(maid, true)) wake(state);
                removeOwnBubble(state);
            }
            return;
        }
        if (canceled || !eligible(maid, false)) { forget(maid); return; }
        if (state != null && (!state.owner.equals(maid.getOwnerUUID()) || state.level != maid.level()
                || state.schedule != maid.getSchedule())) { forget(maid); state = null; }
        if (state == null) { state = new SleepState(maid); SLEEPERS.put(maid, state); }
        boolean chatter = SleepDialogueRules.chatter(state.schedule, state.level.getDayTime());
        if (chatter != state.chatter) {
            state.chatter = chatter; state.nextTalk = -1; state.postponed = false; state.group = null;
            removeOwnBubble(state);
        }
        if (state.bubble != null && occupied(state)) removeOwnBubble(state);
    }

    static boolean eligible(EntityMaid maid, boolean waking) {
        if (maid.isRemoved() || !maid.isAlive() || maid.deathTime != 0 || !maid.isTame() || maid.getOwnerUUID() == null
                || !SleepDialogueRules.supported(maid.getSchedule()) || maid.getTask() instanceof LazyMaidTask
                || BetrayalOutpostMaidData.isOutpostMaid(maid) || EmotionBetrayalManager.isBetraying(maid)
                || WanderingCampData.active(maid) || WanderingMaidData.isSpecial(maid) || TradingMaidData.isTrading(maid)
                || IntimidationManager.isIntimidated(maid) || maid.isNoAi() || maid.isPassenger()
                || PrincessCarryManager.isMaidCarrySession(maid)
                || MaidMovementControl.isActive(maid, MaidMovementControl.Reason.CAGE)) return false;
        // Being hit may immediately activate the existing flee controller. It must not hide a wake line.
        return waking || (maid.getSleepingPos().isPresent() && MaidMovementControl.activeMask(maid) == 0
                && maid.getTarget() == null);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer owner)) return;
        if (SLEEPERS.values().stream().noneMatch(s -> s.owner.equals(owner.getUUID()))) {
            MOTION.remove(owner.getUUID()); return;
        }
        MOTION.computeIfAbsent(owner.getUUID(), ignored -> new OwnerMotion()).sample(owner);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 10 != 0) return;
        pulse();
    }

    static void pulse() {
        for (EntityMaid maid : List.copyOf(SLEEPERS.keySet())) observe(maid, false);
        Set<EntityMaid> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (SleepState seed : List.copyOf(SLEEPERS.values())) {
            if (!seed.chatter || !visited.add(seed.maid)) continue;
            List<SleepState> members = new ArrayList<>();
            ArrayDeque<SleepState> queue = new ArrayDeque<>(); queue.add(seed);
            while (!queue.isEmpty()) {
                SleepState current = queue.removeFirst(); members.add(current);
                for (EntityMaid neighbor : current.level.getEntitiesOfClass(EntityMaid.class,
                        current.maid.getBoundingBox().inflate(8), m -> m.distanceToSqr(current.maid) <= 64)) {
                    SleepState next = SLEEPERS.get(neighbor);
                    if (next != null && next.chatter && next.owner.equals(seed.owner) && visited.add(neighbor)) queue.add(next);
                }
            }
            updateGroup(members);
        }
        for (SleepState state : List.copyOf(SLEEPERS.values())) {
            boolean mayChat = state.chatter && state.group != null && state.group.members.size() >= 2
                    && !state.group.discovered;
            if (state.chatter && !mayChat) { state.nextTalk = -1; state.postponed = false; continue; }
            periodic(state, state.level.getGameTime());
        }
        MOTION.keySet().removeIf(owner -> SLEEPERS.values().stream().noneMatch(s -> s.owner.equals(owner)));
    }

    private static void updateGroup(List<SleepState> members) {
        Group group = new Group();
        for (SleepState state : members) group.members.add(state.maid.getUUID());
        for (SleepState state : members) {
            Group old = state.group;
            if (old != null) {
                group.discovered |= old.discovered;
                if (old.members.equals(group.members)) group.outsideSince = old.outsideSince;
            }
        }
        members.forEach(s -> s.group = group);
        SleepState first = members.get(0);
        ServerPlayer owner = first.level.getServer().getPlayerList().getPlayer(first.owner);
        boolean near = owner != null && owner.isAlive() && !owner.isSpectator() && owner.level() == first.level
                && members.stream().anyMatch(s -> s.maid.distanceToSqr(owner) <= 25);
        long now = first.level.getGameTime();
        if (group.discovered) {
            if (near) group.outsideSince = -1;
            else if (group.outsideSince < 0) group.outsideSince = now;
            else if (now - group.outsideSince >= 200) {
                group.discovered = false; group.outsideSince = -1;
                if (members.size() >= 2) emit(members.get(first.maid.getRandom().nextInt(members.size())), "left", 8, -10);
                for (SleepState state : members) resetTimer(state, now);
            }
        } else if (near && members.size() >= 2) {
            OwnerMotion motion = MOTION.get(first.owner);
            if (owner.isShiftKeyDown() || (motion != null && motion.hidden(owner, members))) return;
            group.discovered = true;
            SleepState closest = members.stream().min(Comparator.comparingDouble(s -> s.maid.distanceToSqr(owner))).orElseThrow();
            emit(closest, "arrived", 5, -10);
        }
    }

    private static void resetTimer(SleepState state, long now) {
        state.nextTalk = now + SleepDialogueRules.delay(state.maid.getRandom(), state.chatter);
        state.postponed = false;
    }

    static void periodic(SleepState state, long now) {
        if (state.nextTalk < 0) { resetTimer(state, now); return; }
        if (now < state.nextTalk) return;
        if (occupied(state) || !state.maid.getConfigManager().isChatBubbleShow()) { resetTimer(state, now); return; }
        boolean recent = state.level.getEntitiesOfClass(EntityMaid.class, state.maid.getBoundingBox().inflate(8),
                m -> m.distanceToSqr(state.maid) <= 64).stream().map(SLEEPERS::get).filter(Objects::nonNull)
                .anyMatch(s -> s.lastBubble != Long.MIN_VALUE && now - s.lastBubble < LOCAL_GAP);
        if (recent) {
            if (!state.postponed) {
                state.postponed = true; state.nextTalk = now + 20 + state.maid.getRandom().nextInt(41);
            } else resetTimer(state, now); // one retry only; never a growing deferred queue
            return;
        }
        if (state.chatter) {
            if (emit(state, "chat", 35, -20)) relieveFear(state);
        } else {
            var values = EmotionData.get(state.maid, state.owner);
            boolean nightmare = state.maid.getRandom().nextInt(100)
                    < SleepDialogueRules.nightmarePercent(values.trust(), values.fear());
            emit(state, nightmare ? "nightmare" : "dream", 25, -30);
        }
        resetTimer(state, now);
    }

    /** The entity's UUID scopes this NBT. Keep both shift slots so changing shift cannot reset an allowance. */
    static void relieveFear(SleepState state) {
        EntityMaid maid = state.maid;
        if (!state.chatter || !maid.isSleeping()) return;
        long day = Math.floorDiv(state.level.getDayTime(), 24000L);
        CompoundTag quota = maid.getPersistentData().getCompound(QUOTA);
        String shift = state.schedule.name();
        CompoundTag slot = quota.getCompound(shift);
        if (!slot.contains("Day", CompoundTag.TAG_LONG) || slot.getLong("Day") != day) {
            slot = new CompoundTag(); slot.putLong("Day", day);
        }
        int used = slot.getInt("Count");
        if (used >= 5 || EmotionData.get(maid, state.owner).fear() <= 0) return;
        EmotionData.addFear(maid, state.owner, -1);
        slot.putInt("Count", Math.max(0, used) + 1);
        quota.put(shift, slot); maid.getPersistentData().put(QUOTA, quota);
    }

    private static void wake(SleepState state) {
        ServerPlayer owner = state.level.getServer().getPlayerList().getPlayer(state.owner);
        int range = state.chatter ? 5 : 8;
        if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.level() != state.level
                || state.maid.distanceToSqr(owner) > range * range) return;
        if (emit(state, state.chatter ? "caught" : "waking", state.chatter ? 12 : 15, -10, true)) {
            // Leave the wake bubble to expire normally while discarding the whole sleep session.
            state.bubble = null; state.bubbleKey = -1;
        }
    }

    static boolean occupied(SleepState state) {
        return occupied(state, false);
    }

    private static boolean occupied(SleepState state, boolean waking) {
        EntityMaid maid = state.maid;
        if (NpcEventData.hasCurrent(maid) || TalkEventManager.isParticipant(maid)
                || maid.getPersistentData().getBoolean("callresponse$dispatch_return_dialogue")) return true;
        long wallNow = System.currentTimeMillis();
        var collection = maid.getChatBubbleManager().getChatBubbleDataCollection();
        for (long key : collection.keySet()) {
            IChatBubbleData bubble = collection.get(key);
            if (key <= wallNow || bubble == state.bubble) continue;
            // Native damage inserts a literal hurt kaomoji before our next observation.
            // Let it coexist with the wake line; never remove it or bypass other text.
            if (waking && maid.hurtTime > 0 && SleepHurtKaomoji.matches(bubble)) continue;
            if (bubble instanceof TextChatBubbleData || bubble instanceof WaitingChatBubbleData
                    || bubble instanceof ProgressChatBubbleData || bubble.priority() > 0) return true;
        }
        return false;
    }

    static boolean emit(SleepState state, String pool, int size, int priority) {
        return emit(state, pool, size, priority, false);
    }

    private static boolean emit(SleepState state, String pool, int size, int priority, boolean waking) {
        EntityMaid maid = state.maid;
        if (!maid.isAlive() || maid.isRemoved() || maid.deathTime != 0 || occupied(state, waking)
                || !maid.getConfigManager().isChatBubbleShow()) return false;
        String prefix = "bubble.callresponse.sleep." + pool + ".";
        int previous = -1;
        if (state.lastLine != null && state.lastLine.startsWith(prefix))
            previous = Integer.parseInt(state.lastLine.substring(prefix.length()));
        String key = prefix + SleepDialogueRules.line(maid.getRandom(), size, previous);
        var manager = maid.getChatBubbleManager();
        var collection = manager.getChatBubbleDataCollection();
        // Never evict a foreign bubble, even a harmless emoji, to insert ambient speech.
        if (collection.size() >= 5 && manager.getChatBubble(state.bubbleKey) != state.bubble) return false;
        removeOwnBubble(state);
        if (collection.size() >= 5) return false;
        var bubble = TextChatBubbleData.create(80, Component.translatable(key), IChatBubbleData.TYPE_2, priority);
        long id = manager.addChatBubble(bubble);
        if (id < 0 || manager.getChatBubble(id) != bubble) return false;
        state.bubbleKey = id; state.bubble = bubble; state.lastLine = key;
        state.lastBubble = state.level.getGameTime();
        return true;
    }

    private static void removeOwnBubble(SleepState state) {
        if (state.bubble != null && state.maid.getChatBubbleManager().getChatBubble(state.bubbleKey) == state.bubble)
            state.maid.getChatBubbleManager().removeChatBubble(state.bubbleKey);
        state.bubble = null; state.bubbleKey = -1;
    }

    private static void forget(EntityMaid maid) {
        SleepState state = SLEEPERS.remove(maid);
        if (state != null) removeOwnBubble(state);
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof EntityMaid maid) forget(maid);
        if (event.getEntity() instanceof ServerPlayer owner) MOTION.remove(owner.getUUID());
    }

    @SubscribeEvent
    public void onReload(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) SleepHurtKaomoji::reload);
    }

    @SubscribeEvent
    public void onStop(ServerStoppedEvent event) { SLEEPERS.clear(); MOTION.clear(); }
}
