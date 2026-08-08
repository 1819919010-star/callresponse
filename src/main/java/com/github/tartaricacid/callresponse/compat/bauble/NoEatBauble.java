package com.github.tartaricacid.callresponse.compat.bauble;

import com.github.tartaricacid.callresponse.compat.hunger.HungerData;
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
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class NoEatBauble extends Item implements IMaidBauble {
    public static final UUID ATTACK_MODIFIER_UUID = UUID.fromString("0a1b2c3d-1111-4222-8333-944455566611");
    public static final UUID MAX_HEALTH_MODIFIER_UUID = UUID.fromString("0a1b2c3d-1111-4222-8333-944455566612");
    private static final float REGEN_AMOUNT = 5.0f;
    private static final float MIN_HUNGER = 10.0f;

    public NoEatBauble(Properties properties) {
        super(properties);
    }

    @Override
    public void onPutOn(EntityMaid maid, ItemStack baubleItem) {
        applyBuffs(maid);
        updateHealthBonus(maid);
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
            // 生命上限随饥饿值动态变化：+（100 - 饥饿值）
            updateHealthBonus(maid);
            if (maid.getHealth() < maid.getMaxHealth()) {
                maid.heal(REGEN_AMOUNT);
            }
            float hunger = HungerData.get(maid);
            if (hunger < MIN_HUNGER) {
                HungerData.add(maid, MIN_HUNGER - hunger);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7佩戴效果："));
        tooltip.add(Component.literal("§a攻击力 §f×2"));
        tooltip.add(Component.literal("§a生命上限 §f+（100 - 当前饥饿值）"));
        tooltip.add(Component.literal("§a每秒回复 §f5 §a点生命"));
        tooltip.add(Component.literal("§a饥饿值不低于 §f10"));
        tooltip.add(Component.literal("§a不会主动进食（玩家投喂除外）"));
    }

    // ===== 拦截 TLM 三餐等主动进食（开饭指令/玩家投喂走 eat() 不经此事件，不受影响） =====
    @SubscribeEvent
    public static void onItemUseStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide) return;
        ItemStack stack = event.getItem();
        if (stack.isEmpty() || stack.getFoodProperties(maid) == null) return;
        if (!BaubleDetector.hasNoEat(maid)) return;
        event.setCanceled(true);
    }

    private void applyBuffs(EntityMaid maid) {
        AttributeInstance attackAttr = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null && attackAttr.getModifier(ATTACK_MODIFIER_UUID) == null) {
            attackAttr.addPermanentModifier(new AttributeModifier(
                    ATTACK_MODIFIER_UUID,
                    "No Eat Attack Bonus",
                    1.0,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    private void updateHealthBonus(EntityMaid maid) {
        AttributeInstance healthAttr = maid.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) return;

        float hunger = HungerData.get(maid);
        float bonus = 100.0f - hunger;
        AttributeModifier current = healthAttr.getModifier(MAX_HEALTH_MODIFIER_UUID);
        if (current != null && Math.abs(current.getAmount() - bonus) < 0.01f) return;

        float oldMax = maid.getMaxHealth();
        float oldHealth = maid.getHealth();
        healthAttr.removeModifier(MAX_HEALTH_MODIFIER_UUID);
        healthAttr.addPermanentModifier(new AttributeModifier(
                MAX_HEALTH_MODIFIER_UUID,
                "No Eat Health Bonus",
                bonus,
                AttributeModifier.Operation.ADDITION
        ));
        float newMax = maid.getMaxHealth();
        if (oldMax > 0 && newMax > 0) {
            maid.setHealth(oldHealth * newMax / oldMax);
        }
    }

    public static void removeBuffs(EntityMaid maid) {
        float oldMax = maid.getMaxHealth();
        float oldHealth = maid.getHealth();
        AttributeInstance attackAttr = maid.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.removeModifier(ATTACK_MODIFIER_UUID);
        }
        AttributeInstance healthAttr = maid.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(MAX_HEALTH_MODIFIER_UUID);
        }
        float newMax = maid.getMaxHealth();
        if (oldMax > 0 && newMax > 0 && oldMax != newMax) {
            maid.setHealth(Math.min(newMax, oldHealth * newMax / oldMax));
        }
    }
}
