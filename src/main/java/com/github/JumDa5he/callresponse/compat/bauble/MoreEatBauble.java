package com.github.JumDa5he.callresponse.compat.bauble;

import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class MoreEatBauble extends Item implements IMaidBauble {
    public static final ResourceLocation ATTACK_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("callresponse", "more_eat_attack");
    public static final ResourceLocation SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("callresponse", "more_eat_speed");
    public static final ResourceLocation MAX_HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("callresponse", "more_eat_health");
    private static final double SPEED_PENALTY = -0.1;

    public MoreEatBauble(Properties properties) {
        super(properties);
    }

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        applyBuffs(maid);
    }

    @Override
    public void onTakeOff(EntityMaid maid, ItemStack baubleItem) {
        removeBuffs(maid);
    }

    @Override
    public void onTick(EntityMaid maid, ItemStack baubleItem) {
        // 属性修改只在服务端执行（modifier 由服务端同步到客户端，客户端改会造成抖动）
        if (maid.level().isClientSide) return;
        applyBuffs(maid);
        if (maid.tickCount % 20 == 0) {
            AttributeInstance attackAttr = maid.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackAttr != null) {
                int hunger = Math.round(HungerData.get(maid));
                AttributeModifier current = attackAttr.getModifier(ATTACK_MODIFIER_ID);
                if (current == null || current.amount() != hunger) {
                    attackAttr.removeModifier(ATTACK_MODIFIER_ID);
                    attackAttr.addPermanentModifier(new AttributeModifier(
                            ATTACK_MODIFIER_ID,
                            hunger,
                            AttributeModifier.Operation.ADD_VALUE
                    ));
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.callresponse.bauble.equip"));
        tooltip.add(Component.translatable("tooltip.callresponse.moreeat_bauble.speed"));
        tooltip.add(Component.translatable("tooltip.callresponse.moreeat_bauble.health"));
        tooltip.add(Component.translatable("tooltip.callresponse.moreeat_bauble.attack"));
        tooltip.add(Component.translatable("tooltip.callresponse.moreeat_bauble.auto_eat"));
    }

    private void applyBuffs(EntityMaid maid) {
        AttributeInstance speedAttr = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(SPEED_MODIFIER_ID) == null) {
            speedAttr.addPermanentModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID,
                    SPEED_PENALTY,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
        AttributeInstance healthAttr = maid.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null && healthAttr.getModifier(MAX_HEALTH_MODIFIER_ID) == null) {
            float oldMax = maid.getMaxHealth();
            float oldHealth = maid.getHealth();
            healthAttr.addPermanentModifier(new AttributeModifier(
                    MAX_HEALTH_MODIFIER_ID,
                    2.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
            float newMax = maid.getMaxHealth();
            if (oldMax > 0) {
                maid.setHealth(oldHealth * newMax / oldMax);
            }
        }
    }

    public static void removeBuffs(EntityMaid maid) {
        float oldMax = maid.getMaxHealth();
        float oldHealth = maid.getHealth();
        AttributeInstance speedAttr = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SPEED_MODIFIER_ID);
        }
        AttributeInstance healthAttr = maid.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
        AttributeInstance attackAttr = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(ATTACK_MODIFIER_ID);
        }
        float newMax = maid.getMaxHealth();
        if (oldMax > 0 && newMax > 0 && oldMax != newMax) {
            maid.setHealth(Math.min(newMax, oldHealth * newMax / oldMax));
        }
    }
}
