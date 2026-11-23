package com.github.JumDa5he.callresponse.compat.emotion;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.MapMaker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.UUID;

/** Attribute bonuses derived from the ordinary maid's relationship with her current owner. */
@EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class EmotionAttributeManager {
    private static final ResourceLocation TRUST = ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "trust_health_bonus");
    private static final ResourceLocation FEAR = ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "fear_attack_bonus");
    private static final Map<EntityMaid, Relationship> relationships = new MapMaker().weakKeys().makeMap();

    private record Relationship(UUID owner, boolean eligible) {
        static Relationship current(EntityMaid maid) {
            return new Relationship(maid.getOwnerUUID(), WineFoxPotions.isOrdinaryMaid(maid));
        }
    }

    // An in-place load can otherwise retain an old modifier in the operation index.
    // Leave current health alone: vanilla restores saved attributes before reading saved Health.
    public static void beforeLoad(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel)) return;
        var health = maid.getAttribute(Attributes.MAX_HEALTH);
        var attack = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        if (health != null) health.removeModifier(TRUST);
        if (attack != null) attack.removeModifier(FEAR);
        relationships.remove(maid);
    }

    public static void refresh(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level) || maid.isRemoved()) return;
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> refresh(maid));
            return;
        }
        var relationship = Relationship.current(maid);
        var values = relationship.eligible() ? EmotionData.get(maid, relationship.owner()) : null;
        update(maid.getAttribute(Attributes.MAX_HEALTH), TRUST, values == null ? 0 : Math.max(0, values.trust()));
        update(maid.getAttribute(Attributes.ATTACK_DAMAGE), FEAR, values == null ? 0 : Math.max(0, values.fear()) / 10.0);
        if (maid.getHealth() > maid.getMaxHealth()) maid.setHealth(maid.getMaxHealth());
        relationships.put(maid, relationship);
    }

    private static void update(AttributeInstance attribute, ResourceLocation id, double amount) {
        if (attribute == null) return;
        var previous = attribute.getModifier(id);
        if (previous != null) {
            if (amount != 0 && previous.operation() == AttributeModifier.Operation.ADD_VALUE && previous.amount() == amount) return;
            attribute.removeModifier(id);
        }
        if (amount > 0) attribute.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof EntityMaid maid) refresh(maid);
    }

    @SubscribeEvent
    public static void tick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof EntityMaid maid && maid.level() instanceof ServerLevel
                && !Relationship.current(maid).equals(relationships.get(maid))) refresh(maid);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        relationships.clear();
    }

    private EmotionAttributeManager() {}
}
