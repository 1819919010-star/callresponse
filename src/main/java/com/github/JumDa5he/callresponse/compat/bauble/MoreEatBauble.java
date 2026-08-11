package com.github.JumDa5he.callresponse.compat.bauble;

import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class MoreEatBauble extends Item implements IMaidBauble {
    public static final UUID ATTACK_MODIFIER_UUID = UUID.fromString("0a1b2c3d-2222-4222-8333-944455566621");
    public static final UUID SPEED_MODIFIER_UUID = UUID.fromString("0a1b2c3d-2222-4222-8333-944455566622");
    public static final UUID MAX_HEALTH_MODIFIER_UUID = UUID.fromString("0a1b2c3d-2222-4222-8333-944455566623");
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
                AttributeModifier current = attackAttr.getModifier(ATTACK_MODIFIER_UUID);
                if (current == null || current.getAmount() != hunger) {
                    attackAttr.removeModifier(ATTACK_MODIFIER_UUID);
                    attackAttr.addPermanentModifier(new AttributeModifier(
                            ATTACK_MODIFIER_UUID,
                            "More Eat Attack Bonus",
                            hunger,
                            AttributeModifier.Operation.ADDITION
                    ));
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7佩戴效果："));
        tooltip.add(Component.literal("§a移动速度 §c-10%"));
        tooltip.add(Component.literal("§a生命上限 §f×3"));
        tooltip.add(Component.literal("§a攻击力 §f+当前饥饿值"));
        tooltip.add(Component.literal("§a每20秒自动进食一次"));
    }

    private void applyBuffs(EntityMaid maid) {
        AttributeInstance speedAttr = maid.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getModifier(SPEED_MODIFIER_UUID) == null) {
            speedAttr.addPermanentModifier(new AttributeModifier(
                    SPEED_MODIFIER_UUID,
                    "More Eat Speed Penalty",
                    SPEED_PENALTY,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
        AttributeInstance healthAttr = maid.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null && healthAttr.getModifier(MAX_HEALTH_MODIFIER_UUID) == null) {
            float oldMax = maid.getMaxHealth();
            float oldHealth = maid.getHealth();
            healthAttr.addPermanentModifier(new AttributeModifier(
                    MAX_HEALTH_MODIFIER_UUID,
                    "More Eat Health Bonus",
                    2.0,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
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
            speedAttr.removeModifier(SPEED_MODIFIER_UUID);
        }
        AttributeInstance healthAttr = maid.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(MAX_HEALTH_MODIFIER_UUID);
        }
        AttributeInstance attackAttr = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(ATTACK_MODIFIER_UUID);
        }
        float newMax = maid.getMaxHealth();
        if (oldMax > 0 && newMax > 0 && oldMax != newMax) {
            maid.setHealth(Math.min(newMax, oldHealth * newMax / oldMax));
        }
    }
}
