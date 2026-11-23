package com.github.JumDa5he.callresponse.compat;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/** Reach used by the carry and status controls in the Forge version. */
public final class MaidInteractionReach {
    // A local calculation only. The live player's attribute and its modifiers stay intact.
    private static final Holder<Attribute> REACH = Holder.direct(
            new RangedAttribute("attribute.name.player.entity_interaction_range", 3, 0, 1024));
    private static final ResourceLocation CREATIVE_MODIFIER = ResourceLocation.withDefaultNamespace("creative_mode_entity_range");

    public static boolean canReach(ServerPlayer player, Entity target, double padding) {
        AttributeInstance effective = new AttributeInstance(REACH, ignored -> {});
        effective.replaceFrom(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE));
        // In 1.20 creative added three blocks *after* all modifiers, only for a nonzero range.
        effective.removeModifier(CREATIVE_MODIFIER);
        double reach = effective.getValue();
        if (player.isCreative() && reach > 0) reach += 3;
        double limit = reach + padding;
        return target.getBoundingBox().inflate(target.getPickRadius()).distanceToSqr(player.getEyePosition()) < limit * limit;
    }

    private MaidInteractionReach() {}
}
