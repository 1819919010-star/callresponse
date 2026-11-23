package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampData;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxSurrender;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.MapMaker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;

/** Per-maid, current-owner emotion bonuses; independent of the existing devoted/doting modifiers. */
public final class EmotionAttributeManager {
    public static final UUID TRUST_HEALTH_UUID = UUID.fromString("91637447-e909-47c1-95dc-b6d37b629967");
    public static final UUID FEAR_ATTACK_UUID = UUID.fromString("28b031d2-6708-422f-8a42-931a730085a7");
    private static final Map<EntityMaid, Relationship> RELATIONSHIPS = new MapMaker().weakKeys().makeMap();

    private static Relationship relationship(EntityMaid maid) {
        UUID owner = maid.getOwnerUUID();
        boolean eligible = maid.isTame() && owner != null
                && (owner.getMostSignificantBits() != 0 || owner.getLeastSignificantBits() != 0)
                && !BetrayalOutpostMaidData.isOutpostMaid(maid)
                && !WineFoxSurrender.isSurrendered(maid)
                && !WanderingMaidData.isSpecial(maid) && !WanderingCampData.active(maid);
        return new Relationship(owner, eligible);
    }

    /** Clear only our previous values before vanilla restores Attributes and then Health. */
    public static void beforeLoad(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel)) return;
        // /data and other reload paths may reuse an existing entity. AttributeInstance.load does
        // not clear its operation sets, so retaining an old UUID with a different amount can
        // leave that old amount active even after the saved modifier replaces the UUID lookup.
        // Do not clamp Health here: vanilla must restore the saved maximum before saved Health.
        AttributeInstance health = maid.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance attack = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        if (health != null) health.removeModifier(TRUST_HEALTH_UUID);
        if (attack != null) attack.removeModifier(FEAR_ATTACK_UUID);
        RELATIONSHIPS.remove(maid);
    }

    /** Call after emotion/identity data is ready; never increase current health with the maximum. */
    public static void refresh(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || maid.isRemoved()) return;
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> refresh(maid));
            return;
        }
        Relationship relation = relationship(maid);
        EmotionData.EmotionValues values = relation.eligible()
                ? EmotionData.get(maid, relation.owner()) : new EmotionData.EmotionValues(0, 0);
        updateAdditiveModifier(maid.getAttribute(Attributes.MAX_HEALTH), TRUST_HEALTH_UUID,
                "callresponse.trust_health_bonus", Math.max(0, values.trust()));
        updateAdditiveModifier(maid.getAttribute(Attributes.ATTACK_DAMAGE), FEAR_ATTACK_UUID,
                "callresponse.fear_attack_bonus", Math.max(0, values.fear()) / 10.0);
        if (maid.getHealth() > maid.getMaxHealth()) maid.setHealth(maid.getMaxHealth());
        Relationship previous = RELATIONSHIPS.put(maid, relation);
        if (relation.eligible()) PlayerMaidResonance.markOwner(maid, relation.owner());
        if (previous != null && previous.eligible() && !previous.equals(relation)) {
            PlayerMaidResonance.markOwner(maid, previous.owner());
        }
    }

    static void updateAdditiveModifier(AttributeInstance attribute, UUID id, String name, double amount) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && current.getOperation() == AttributeModifier.Operation.ADDITION
                && Double.compare(current.getAmount(), amount) == 0 && amount != 0) return;
        if (current != null) attribute.removeModifier(id);
        // Persist these modifiers: vanilla loads attributes before saved Health. A transient-only
        // maximum would clamp legitimate saved health down to the unboosted maximum before join.
        if (amount != 0)
            attribute.addPermanentModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADDITION));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onMaidJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof EntityMaid maid) refresh(maid);
    }

    @SubscribeEvent
    public void onMaidTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide) return;
        // Taming/ownership/identity setters also exist in other mods. Observe changes without
        // removing or re-adding modifiers on every tick or requiring the owner to be online.
        Relationship current = relationship(maid);
        if (!current.equals(RELATIONSHIPS.get(maid))) refresh(maid);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) { RELATIONSHIPS.clear(); }

    private record Relationship(UUID owner, boolean eligible) {}
}
