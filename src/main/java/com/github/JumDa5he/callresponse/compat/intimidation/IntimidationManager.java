package com.github.JumDa5he.callresponse.compat.intimidation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.brain.JealousyCageManager;
import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.JumDa5he.callresponse.compat.broadcast.DialogueApiLimiter;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionPrompt;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionDotingManager;
import com.github.JumDa5he.callresponse.compat.emotion.EmotionForgettingManager;
import com.github.JumDa5he.callresponse.compat.emotion.FearPanicManager;
import com.github.JumDa5he.callresponse.compat.npc.NpcEventManager;
import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.JumDa5he.callresponse.compat.talk.TalkDialogueBridge;
import com.github.JumDa5he.callresponse.config.BroadcastConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Only runtime AI gating is stored here; no Mob NoAI/NBT field is changed. */
public final class IntimidationManager {
    private static final String COOLDOWN_TAG = "CallResponseIntimidationCooldownTicks";
    private static final int[] KILL_THRESHOLDS = {0, 1, 10, 100, 1000};
    private static final int[] RADII = {16, 24, 32, 48, 64};
    private static final int[] COOLDOWN_MINUTES = {15, 12, 8, 3, 1};
    private static final int[] DURATION_SECONDS = {8, 10, 12, 15, 20};
    /** 非玩家来源：女仆看到别的女仆被游行示众。 */
    private static final UUID PARADE_SOURCE = new UUID(0L, 0L);
    private static final Map<UUID, Control> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> GENERATIONS = new ConcurrentHashMap<>();
    private static final Deque<Reply> REPLIES = new ArrayDeque<>();
    private static final Map<UUID, PendingAiReply> PENDING_AI = new HashMap<>();
    private static long nextReplyTick;

    private record Tier(int level, int radius, int cooldownTicks, int durationTicks) {
    }

    private static Tier tierForKills(int kills) {
        int index = 0;
        for (int i = 1; i < KILL_THRESHOLDS.length && kills >= KILL_THRESHOLDS[i]; i++) index = i;
        return tierForLevel(index + 1);
    }

    private static Tier tierForLevel(int level) {
        if (level < 1 || level > KILL_THRESHOLDS.length) {
            throw new IllegalArgumentException("Intimidation level must be 1..5");
        }
        int index = level - 1;
        return new Tier(level, RADII[index], COOLDOWN_MINUTES[index] * 60 * 20,
                DURATION_SECONDS[index] * 20);
    }

    private static final class Control {
        final Mob mob;
        final Map<UUID, Long> sources = new ConcurrentHashMap<>();
        UUID lastCaster;
        boolean wasDoting;
        boolean resetForgetting;
        UUID dangerOwner;
        long visualUntil;

        Control(Mob mob) {
            this.mob = mob;
        }
    }

    private record Reply(EntityMaid maid, UUID casterId, int generation, long dueTick) {
    }

    private static final class PendingAiReply {
        final EntityMaid maid;
        final int generation;
        final String fallbackKey;
        final long deadline;
        UUID requestId;

        PendingAiReply(EntityMaid maid, int generation, String fallbackKey, long deadline) {
            this.maid = maid;
            this.generation = generation;
            this.fallbackKey = fallbackKey;
            this.deadline = deadline;
        }
    }

    public static boolean isIntimidated(Entity entity) {
        if (!(entity instanceof Mob mob) || mob.level().isClientSide) return false;
        Control control = ACTIVE.get(mob.getUUID());
        return control != null && control.mob == mob && !control.sources.isEmpty();
    }

    /**
     * 非玩家来源的威慑：女仆看到别的女仆正在被游行示众。
     * <p>和玩家威压共用同一套状态（暂停自主 AI、受惊表现、后续回应），只是来源不依附玩家，
     * 所以不会因为“施法者不在线”被清掉，也不会自己刷回应气泡。
     */
    public static void intimidateByParade(Mob mob, int durationTicks) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        long now = level.getServer().getTickCount();
        Control control = ACTIVE.get(mob.getUUID());
        if (control == null || control.mob != mob) {
            control = new Control(mob);
            ACTIVE.put(mob.getUUID(), control);
            if (mob instanceof EntityMaid maid) {
                GENERATIONS.merge(maid.getUUID(), 1, Integer::sum);
                cancelPendingAi(maid.getUUID());
            }
            // 正在进行的自主寻路必须先停下，和玩家威压一致
            mob.getNavigation().stop();
        }
        control.sources.merge(PARADE_SOURCE, now + durationTicks, Math::max);
        syncVisual(control, now);
    }

    /** Captured by our own asynchronous LLM callbacks to invalidate pre-cast speech. */
    public static int generation(EntityMaid maid) {
        return GENERATIONS.getOrDefault(maid.getUUID(), 0);
    }

    public static void cast(ServerPlayer caster) {
        if (!caster.isAlive() || caster.isSpectator()) return;
        CompoundTag data = caster.getPersistentData();
        int remaining = data.getInt(COOLDOWN_TAG);
        if (remaining > 0) {
            caster.displayClientMessage(Component.translatable("message.callresponse.intimidation.cooldown",
                    Math.max(1, (remaining + 19) / 20)), true);
            return;
        }
        int kills = Math.max(0, caster.getStats().getValue(Stats.ENTITY_KILLED.get(EntityMaid.TYPE)));
        Tier tier = tierForKills(kills);
        data.putInt(COOLDOWN_TAG, tier.cooldownTicks());
        performCast(caster, tier);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("callresponse")
                .then(Commands.literal("intimidation")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                .executes(context -> testCast(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "level"))))));
    }

    private static int testCast(CommandSourceStack source, int level) {
        if (!(source.getEntity() instanceof ServerPlayer caster) || !caster.isAlive() || caster.isSpectator()) {
            source.sendFailure(Component.translatable("command.callresponse.intimidation.player_only"));
            return 0;
        }
        int affected = performCast(caster, tierForLevel(level));
        source.sendSuccess(() -> Component.translatable("command.callresponse.intimidation.result",
                level, affected), false);
        return 1;
    }

    private static int performCast(ServerPlayer caster, Tier tier) {
        long now = caster.server.getTickCount();
        caster.displayClientMessage(Component.translatable("message.callresponse.intimidation.cast",
                tier.level()), true);
        caster.serverLevel().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.0F, 0.85F);
        double radiusSq = (double) tier.radius() * tier.radius();
        int affected = 0;
        for (Mob mob : caster.serverLevel().getEntitiesOfClass(Mob.class,
                caster.getBoundingBox().inflate(tier.radius()),
                target -> target.isAlive() && !target.isRemoved()
                        && target.distanceToSqr(caster) <= radiusSq)) {
            try {
                control(caster, mob, now + tier.durationTicks());
                if (isIntimidated(mob)) {
                    showIntimidationParticles(caster.serverLevel(), mob, 8);
                    // A single normal damage attempt per cast, not a per-tick drain or owner attack.
                    mob.hurt(mob.damageSources().magic(), 1.0F);
                    affected++;
                }
            } catch (RuntimeException ex) {
                CallResponseMod.LOGGER.warn("Intimidation skipped entity {} after a safe failure",
                        mob.getUUID(), ex);
                release(mob, false);
            }
        }
        return affected;
    }

    private static void control(ServerPlayer caster, Mob mob, long untilTick) {
        Control control = ACTIVE.get(mob.getUUID());
        boolean first = control == null || control.mob != mob;
        if (first) {
            control = new Control(mob);
            ACTIVE.put(mob.getUUID(), control);
            if (mob instanceof EntityMaid maid) {
                GENERATIONS.merge(maid.getUUID(), 1, Integer::sum);
                cancelPendingAi(maid.getUUID());
            }
            // Existing autonomous navigation must not continue during the gate.
            mob.getNavigation().stop();
        }
        control.sources.merge(caster.getUUID(), untilTick, Math::max);
        control.lastCaster = caster.getUUID();
        if (mob instanceof EntityMaid maid) handleMaidHit(caster, maid, control);
        syncVisual(control, caster.server.getTickCount());
    }

    private static void syncVisual(Control control, long now) {
        if (!(control.mob instanceof EntityMaid maid)) return;
        if (CuteActivityBridge.isRevengeMaid(maid)) return;
        long until = control.sources.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        if (until == control.visualUntil) return;
        control.visualUntil = until;
        CuteActivityBridge.syncToTrackers(maid, (int) Math.max(0L, until - now));
    }

    private static void handleMaidHit(ServerPlayer caster, EntityMaid maid, Control control) {
        UUID ownerId = maid.getOwnerUUID();
        boolean own = ownerId != null && ownerId.equals(caster.getUUID());
        boolean relationshipAllowed = maid.isTame() && own;
        // The generic AI gate still affects wild and already-betrayed maids.
        if (relationshipAllowed) {
            EmotionData.EmotionValues before = EmotionData.get(maid, ownerId);
            control.wasDoting |= before.trust() >= 90 && before.fear() <= 10;
            if (EmotionBetrayalManager.hasActiveDangerTimer(maid)) control.dangerOwner = ownerId;
            control.resetForgetting = true;
            // Clear persisted countdowns before the entity can unload while gated.
            EmotionForgettingManager.resetAfterIntimidation(maid);
        }
        // Existing callresponse interactions close through their own safe exit paths.
        TalkEventManager.abortForIntimidation(maid);
        JealousyCageManager.abortForIntimidation(maid);
        EmotionDotingManager.abortForIntimidation(maid);
        FearPanicManager.abortForIntimidation(maid);
        if (PrincessCarryManager.isMaidCarrySession(maid)) PrincessCarryManager.releaseCarrier(maid);
        if (maid.getVehicle() instanceof EntityMaid carrier
                && PrincessCarryManager.isMaidCarrySession(carrier, maid)) {
            PrincessCarryManager.releaseCarrier(carrier);
        }
        PrincessCarryManager.cancelRequest(maid);
        maid.getNavigation().stop();
        if (relationshipAllowed) {
            NpcEventManager.settleExistingSecondChoices(maid, ownerId);
            EmotionData.addFear(maid, ownerId, 5);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CompoundTag data = player.getPersistentData();
            int ticks = data.getInt(COOLDOWN_TAG);
            if (ticks > 0) data.putInt(COOLDOWN_TAG, ticks - 1);
        }
        for (Control control : new ArrayList<>(ACTIVE.values())) {
            Mob mob = control.mob;
            if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel)) {
                release(mob, false);
                continue;
            }
            boolean interrupted = false;
            for (Map.Entry<UUID, Long> entry : new ArrayList<>(control.sources.entrySet())) {
                if (PARADE_SOURCE.equals(entry.getKey())) {
                    // 环境来源没有施法者，只看倒计时
                    if (entry.getValue() <= now) control.sources.remove(entry.getKey());
                    continue;
                }
                ServerPlayer caster = server.getPlayerList().getPlayer(entry.getKey());
                if (caster == null || caster.level() != mob.level()) {
                    interrupted = true;
                    control.sources.remove(entry.getKey());
                } else if (entry.getValue() <= now) control.sources.remove(entry.getKey());
            }
            if (control.sources.isEmpty()) release(mob, !interrupted);
            else {
                syncVisual(control, now);
                if ((now + mob.getId()) % 10L == 0L && mob.level() instanceof ServerLevel level) {
                    showIntimidationParticles(level, mob, 3);
                }
                if (mob instanceof EntityMaid maid) EmotionBetrayalManager.pauseOutpostReturnClock(maid);
            }
        }
        for (PendingAiReply pending : new ArrayList<>(PENDING_AI.values())) {
            if (now < pending.deadline || !PENDING_AI.remove(pending.maid.getUUID(), pending)) continue;
            if (pending.requestId != null) TalkDialogueBridge.cancel(pending.requestId);
            addFallbackIfValid(pending);
        }
        if (now >= nextReplyTick && !REPLIES.isEmpty()) {
            Reply reply = REPLIES.removeFirst();
            nextReplyTick = now + 5;
            if (reply.dueTick() <= now) deliverReply(server, reply);
            else REPLIES.addLast(reply);
        }
    }

    private static void showIntimidationParticles(ServerLevel level, Mob mob, int count) {
        double spread = Math.max(0.25D, mob.getBbWidth() * 0.5D);
        level.sendParticles(ParticleTypes.WITCH, mob.getX(), mob.getY() + mob.getBbHeight() * 0.55D,
                mob.getZ(), count, spread, Math.max(0.25D, mob.getBbHeight() * 0.4D), spread, 0.01D);
    }

    private static void release(Mob mob, boolean respond) {
        Control control = ACTIVE.get(mob.getUUID());
        if (control == null || control.mob != mob) return;
        ACTIVE.remove(mob.getUUID());
        if (!(mob instanceof EntityMaid maid)) return;
        if (control.visualUntil > 0L) CuteActivityBridge.syncToTrackers(maid, 0);
        if (!respond) cancelPendingAi(maid.getUUID());
        if (respond && control.wasDoting && maid.isAlive()) EmotionDotingManager.startCalmPeriod(maid);
        if (control.dangerOwner != null) {
            if (respond && maid.isAlive())
                EmotionBetrayalManager.resumeDangerAfterIntimidation(maid, control.dangerOwner);
            else EmotionBetrayalManager.cancelDangerAfterIntimidation(maid);
        }
        if (control.resetForgetting && maid.isAlive()) EmotionForgettingManager.resetAfterIntimidation(maid);
        if (respond && control.lastCaster != null && maid.isAlive() && !maid.isRemoved()) {
            REPLIES.addLast(new Reply(maid, control.lastCaster, generation(maid),
                    mob.level().getServer().getTickCount() + 5));
        }
    }

    private static void deliverReply(MinecraftServer server, Reply reply) {
        EntityMaid maid = reply.maid();
        if (!maid.isAlive() || maid.isRemoved() || isIntimidated(maid)
                || generation(maid) != reply.generation()) return;
        ServerPlayer caster = server.getPlayerList().getPlayer(reply.casterId());
        if (caster == null || caster.level() != maid.level()) return;
        boolean owner = maid.getOwnerUUID() != null && maid.getOwnerUUID().equals(caster.getUUID());
        String state = responseState(maid, owner);
        String key = "bubble.callresponse.intimidation." + state + "."
                + (1 + maid.getRandom().nextInt(state.equals("other") ? 1 : 2));
        if (!BroadcastConfig.NPC_EVENT_AI_REPLY_ENABLED.get() || !DialogueApiLimiter.tryAcquire()) {
            maid.getChatBubbleManager().addTextChatBubble(key);
            return;
        }
        String prompt = owner
                ? "主人刚刚释放了短暂威压，你现在已经恢复行动。按你当前的信任、恐惧和关系状态，用一句自然的话回应主人；不提数值、技能系统或动作指令。"
                : "眼前玩家刚释放了短暂威压，你现在已经恢复行动。这个玩家不是你的主人，绝对不要称他为主人。用一句符合当前情绪的自然话回应。";
        if (owner) prompt += EmotionPrompt.buildEmotionContext(maid, caster);
        int generation = reply.generation();
        PendingAiReply pending = new PendingAiReply(maid, generation, key, server.getTickCount() + 400L);
        PENDING_AI.put(maid.getUUID(), pending);
        pending.requestId = TalkDialogueBridge.request(maid, caster, prompt,
                ignored -> PENDING_AI.remove(maid.getUUID(), pending), () -> {
                    if (PENDING_AI.remove(maid.getUUID(), pending)) addFallbackIfValid(pending);
                });
    }

    private static void addFallbackIfValid(PendingAiReply pending) {
        EntityMaid maid = pending.maid;
        if (maid.isAlive() && !maid.isRemoved() && !isIntimidated(maid)
                && generation(maid) == pending.generation) {
            maid.getChatBubbleManager().addTextChatBubble(pending.fallbackKey);
        }
    }

    private static void cancelPendingAi(UUID maidId) {
        PendingAiReply pending = PENDING_AI.remove(maidId);
        if (pending != null && pending.requestId != null) TalkDialogueBridge.cancel(pending.requestId);
    }

    private static String responseState(EntityMaid maid, boolean owner) {
        if (!owner) return EmotionBetrayalManager.isBetraying(maid) ? "betrayal" : "other";
        if (EmotionBetrayalManager.isBetraying(maid)) return "betrayal";
        EmotionData.EmotionValues values = EmotionData.get(maid, maid.getOwnerUUID());
        if (values.trust() >= 80 && values.fear() >= 80) return "devoted";
        if (values.trust() >= 90 && values.fear() <= 10) return "doting";
        if (values.trust() <= 10 && values.fear() <= 10) return "forgotten";
        return "normal";
    }

    private static void removeCaster(UUID casterId) {
        for (Control control : new ArrayList<>(ACTIVE.values())) {
            control.sources.remove(casterId);
            if (control.sources.isEmpty()) release(control.mob, false);
            else syncVisual(control, control.mob.level().getServer().getTickCount());
        }
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof EntityMaid maid) {
            Control control = ACTIVE.get(maid.getUUID());
            long until = control != null && control.mob == maid
                    ? control.sources.values().stream().mapToLong(Long::longValue).max().orElse(0L) : 0L;
            CuteActivityBridge.syncToPlayer(maid, player,
                    (int) Math.max(0L, until - player.server.getTickCount()));
        }
    }

    @SubscribeEvent
    public void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof EntityMaid maid) {
            CuteActivityBridge.clearForPlayer(maid, player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        removeCaster(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        removeCaster(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        int remaining = event.getOriginal().getPersistentData().getInt(COOLDOWN_TAG);
        if (remaining > 0) event.getEntity().getPersistentData().putInt(COOLDOWN_TAG, remaining);
    }

    @SubscribeEvent
    public void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) release(mob, false);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob) release(mob, false);
    }

    @SubscribeEvent
    public void onServerStop(ServerStoppingEvent event) {
        for (UUID maidId : new ArrayList<>(PENDING_AI.keySet())) cancelPendingAi(maidId);
        ACTIVE.clear();
        GENERATIONS.clear();
        REPLIES.clear();
        nextReplyTick = 0L;
    }
}
