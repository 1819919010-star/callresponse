package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

/** Native lifetimes with bonuses restricted to the requested player/ordinary-maid identities. */
public final class WineFoxEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
            Registries.MOB_EFFECT, CallResponseMod.MOD_ID);
    public static final DeferredHolder<MobEffect, MobEffect> CORRUPTED_HUNT = EFFECTS.register(
            "corrupted_hunt", CorruptedHunt::new);
    public static final DeferredHolder<MobEffect, MobEffect> SWEETNESS = EFFECTS.register("sweetness", Sweetness::new);

    private abstract static class PotionEffect extends MobEffect {
        private PotionEffect(MobEffectCategory category, int color) { super(category, color); }

        @Override public void onEffectStarted(LivingEntity entity, int amplifier) {
            WineFoxPotions.reconcile(entity);
        }
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }
        @Override public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            // Also covers external forceAddEffect, which skips both Added and onEffectStarted in 1.21.
            WineFoxPotions.reconcile(entity);
            return true;
        }
        @Override public void addAttributeModifiers(AttributeMap attributes, int amplifier) {
            // 1.21 removed the entity argument here. The local effect callbacks above check identity.
        }
    }

    private static final class CorruptedHunt extends PotionEffect {
        private CorruptedHunt() { super(MobEffectCategory.HARMFUL, 0x5D267E); }

        @Override public void removeAttributeModifiers(AttributeMap attributes) {
            var attack = attributes.getInstance(Attributes.ATTACK_DAMAGE);
            var speed = attributes.getInstance(Attributes.ATTACK_SPEED);
            if (attack != null) attack.removeModifier(WineFoxPotions.MALICE_ATTACK_ID);
            if (speed != null) speed.removeModifier(WineFoxPotions.MALICE_SPEED_ID);
        }
    }

    private static final class Sweetness extends PotionEffect {
        private Sweetness() {
            super(MobEffectCategory.BENEFICIAL, 0xF3ADCB);
            // Constant curves keep command amplifiers from scaling these fixed bonuses or tooltips.
            addAttributeModifier(Attributes.ATTACK_DAMAGE, WineFoxPotions.SWEET_ATTACK_ID,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, amplifier -> WineFoxPotions.SWEET_ATTACK_BONUS);
            addAttributeModifier(Attributes.ENTITY_INTERACTION_RANGE, WineFoxPotions.SWEET_REACH_ID,
                    AttributeModifier.Operation.ADD_VALUE, amplifier -> 2);
        }
    }
    private WineFoxEffects() {}
}
