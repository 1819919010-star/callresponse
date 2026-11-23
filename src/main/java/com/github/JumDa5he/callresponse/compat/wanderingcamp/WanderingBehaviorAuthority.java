package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.google.common.collect.MapMaker;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.ai.Brain;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/** Narrow concrete speech scope, Brain binding and stale reply invalidation; no movement privileges. */
public final class WanderingBehaviorAuthority {
    /** Implemented only by a signature-checked optional addon's own combat record. */
    public interface WildCombatState { void callresponse$discardCampCombat(); }
    private static final ThreadLocal<EntityMaid> SPEAKING = new ThreadLocal<>();
    private static final Map<Brain<?>, WeakReference<EntityMaid>> OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    // Weak keys use object identity: TLM's collection record has mutable, content-based equality.
    private static final Map<Object, WeakReference<EntityMaid>> BUBBLES = new MapMaker().weakKeys().makeMap();
    private static final Map<EntityMaid, Long> DIALOGUE_EPOCHS = Collections.synchronizedMap(new WeakHashMap<>());
    private WanderingBehaviorAuthority() {}

    public static boolean rejectsDialogue(EntityMaid maid) {
        return CampPolicy.blocksExternalDialogue(maid) && SPEAKING.get() != maid;
    }
    /** Speech authorization is separate from movement, and covers only the concrete send. */
    public static void speak(EntityMaid maid, Runnable send) {
        if (maid.level().isClientSide || maid.level().getServer() == null || !maid.level().getServer().isSameThread())
            throw new IllegalStateException("Camp speech requires the server thread");
        EntityMaid previous = SPEAKING.get();
        SPEAKING.set(maid);
        try { send.run(); }
        finally { if (previous == null) SPEAKING.remove(); else SPEAKING.set(previous); }
    }
    public static boolean blocksDamage(DamageSource source) {
        return campAttacker(source.getEntity()) || campAttacker(source.getDirectEntity());
    }
    private static boolean campAttacker(Entity entity) {
        // Bounded owner traversal also covers projectiles whose source names only the projectile.
        for (int depth = 0; entity != null && depth < 4; depth++) {
            if (entity instanceof EntityMaid maid)
                return CampPolicy.nonCombat(maid);
            if (!(entity instanceof Projectile projectile)) return false;
            entity = projectile.getOwner();
        }
        return false;
    }
    public static void bindBubbles(Object collection, EntityMaid maid) {
        if (WanderingCampData.active(maid)) BUBBLES.put(collection, new WeakReference<>(maid));
    }
    public static boolean rejectsBubble(Object collection) {
        var ref = BUBBLES.get(collection);
        EntityMaid maid = ref == null ? null : ref.get();
        return maid != null && rejectsDialogue(maid);
    }
    public static boolean spokenBubble(com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData bubble) {
        return bubble instanceof com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData
                || bubble instanceof com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.WaitingChatBubbleData;
    }
    public record ExternalReply(EntityMaid maid, long epoch, boolean rejected) {
        public boolean valid() {
            return !rejected && !WanderingCampData.active(maid) && epoch == dialogueEpoch(maid);
        }
        public Runnable guard(Runnable delivery) { return () -> { if (valid()) delivery.run(); }; }
    }
    public static ExternalReply externalReply(EntityMaid maid) {
        return new ExternalReply(maid, dialogueEpoch(maid), WanderingCampData.active(maid));
    }
    private static long dialogueEpoch(EntityMaid maid) { return DIALOGUE_EPOCHS.getOrDefault(maid, 0L); }

    /** Bound only at this maid's own Brain registration/refresh, never inferred from nearby entities. */
    public static void bind(Brain<?> brain, EntityMaid maid) {
        if (!maid.level().isClientSide && WanderingCampData.active(maid)) {
            OWNERS.put(brain, new WeakReference<>(maid));
            DIALOGUE_EPOCHS.putIfAbsent(maid, 1L);
        }
    }
    public static void unbind(Brain<?> brain) {
        var ref = OWNERS.remove(brain);
        EntityMaid maid = ref == null ? null : ref.get();
        if (maid != null) DIALOGUE_EPOCHS.put(maid, dialogueEpoch(maid) + 1);
    }
    public static boolean bound(Brain<?> brain, EntityMaid maid) {
        var ref = OWNERS.get(brain);
        return ref != null && ref.get() == maid;
    }
}
