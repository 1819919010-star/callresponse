package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Native effect lifetimes; only the two requested identities receive their local modifiers. */
public final class WineFoxEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
            ForgeRegistries.MOB_EFFECTS, CallResponseMod.MOD_ID);
    public static final RegistryObject<MobEffect> CORRUPTED_HUNT = EFFECTS.register(
            "corrupted_hunt", CorruptedHunt::new);
    public static final RegistryObject<MobEffect> SWEETNESS = EFFECTS.register("sweetness", Sweetness::new);

    private static final class CorruptedHunt extends MobEffect {
        private CorruptedHunt() {
            super(MobEffectCategory.HARMFUL, 0x5D267E);
        }
        @Override public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
            // These bonuses are maid-only. Do not advertise them as generic player potion attributes.
            WineFoxPotions.maliceModifiers(entity, entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid
                    && WineFoxPotions.isOrdinaryMaid(maid));
            WineFoxPotions.ensureMaliceLevel(entity, amplifier);
        }
        @Override public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
            WineFoxPotions.maliceModifiers(entity, false);
            // onEffectUpdated also invokes this, with the replacement still present in activeEffects.
            if (!entity.hasEffect(this)) WineFoxPotions.clearMaliceLevel(entity);
        }
    }

    private static final class Sweetness extends MobEffect {
        private Sweetness() {
            super(MobEffectCategory.BENEFICIAL, 0xF3ADCB);
            addAttributeModifier(Attributes.ATTACK_DAMAGE, WineFoxPotions.SWEET_ATTACK_UUID.toString(),
                    WineFoxPotions.SWEET_ATTACK_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL);
            addAttributeModifier(ForgeMod.ENTITY_REACH.get(), WineFoxPotions.SWEET_REACH_UUID.toString(),
                    2, AttributeModifier.Operation.ADDITION);
        }
        @Override public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
            return modifier.getAmount();
        }
        @Override public void addAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
            if (WineFoxPotions.canReceiveSweetness(entity)) super.addAttributeModifiers(entity, attributes, 0);
        }
    }
    private WineFoxEffects() {}
}
