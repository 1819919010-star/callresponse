package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.brain.LazyMaidHitHandler;
import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageSource;
import com.github.JumDa5he.callresponse.compat.talk.TalkEventManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** One rolling cooldown for all immediate hurt emotions; never persisted or migrated. */
public final class EmotionHurtSettlement {
    private static final Map<EntityMaid, Integer> LAST_HURT = new WeakHashMap<>();
    private EmotionHurtSettlement() {}

    public static void apply(EntityMaid maid, DamageSource source, float damage) {
        if (maid.level().isClientSide || !Float.isFinite(damage) || damage <= 0
                || !maid.isTame() || maid.getOwnerUUID() == null
                || EmotionBetrayalManager.isBetraying(maid)) return;
        UUID ownerId = maid.getOwnerUUID();
        ServerPlayer player = OwnerDamageSource.findServerPlayer(maid, source);
        boolean ownerHit = OwnerDamageSource.isCurrentOwnerSource(maid, source)
                && player != null && source.getDirectEntity() == source.getEntity()
                && aimingAt(player, maid);
        boolean generalHit = maid.getOwner() != null && !TalkEventManager.isParticipant(maid);
        boolean lazyHit = player != null && OwnerDamageSource.isCurrentOwnerSource(maid, source)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.STARVE)
                && LazyMaidHitHandler.isLazyMode(maid);
        if (!ownerHit && !generalHit && !lazyHit) return;
        int now = maid.level().getServer().getTickCount();
        Integer last = LAST_HURT.get(maid);
        if (last != null && now >= last && now - last < 20) {
            // The cooldown limits trust/fear only. Keep each effective hit's existing
            // escape/feedback behavior and its own independent dialogue rules.
            react(maid, player, lazyHit, ownerHit);
            return;
        }
        LAST_HURT.put(maid, now);

        // Aggregate both valid subitems before taking a single cooldown slot.
        float fear = generalHit ? 0.1F : 0;
        float trust = generalHit ? -0.1F : 0;
        if (lazyHit) fear += Math.round(0.5F);
        if (ownerHit) {
            // Keep the old owner-hit formula's original input; the existing scoped damage
            // context already holds it while this final positive-damage hook is running.
            float ownerDamage = com.github.JumDa5he.callresponse.compat.damage.OwnerDamageContext.rawDamage(maid, damage);
            fear += Math.min((int) (1 + ownerDamage * 1.5F), 4);
            trust += Math.max(-(1 + (int) (ownerDamage * 0.5F)), -2);
        }
        EmotionData.addFearFloat(maid, ownerId, fear);
        EmotionData.addTrustFloat(maid, ownerId, trust);
        react(maid, player, lazyHit, ownerHit);
    }

    private static void react(EntityMaid maid, ServerPlayer player, boolean lazyHit, boolean ownerHit) {
        if (lazyHit) LazyMaidHitHandler.triggerEscape(maid, player);
        else if (ownerHit) EmotionActiveDialogue.tryInteractDialogue(maid, player);
    }

    private static boolean aimingAt(ServerPlayer player, EntityMaid maid) {
        var eye = player.getEyePosition(1.0F);
        var direction = player.getLookAngle().scale(4.5D);
        var hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, eye.add(direction),
                player.getBoundingBox().expandTowards(direction).inflate(1),
                e -> e == maid || e.isPickable());
        if (hit == null || hit.getEntity() != maid) return false;
        var maidEye = maid.getEyePosition();
        var block = player.level().clip(new ClipContext(eye, maidEye, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return block.getType() == HitResult.Type.MISS
                || block.getLocation().distanceToSqr(eye) >= eye.distanceToSqr(maidEye);
    }
}
