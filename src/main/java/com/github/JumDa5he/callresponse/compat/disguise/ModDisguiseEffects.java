package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModDisguiseEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
            BuiltInRegistries.MOB_EFFECT, CallResponseMod.MOD_ID);
    public static final DeferredHolder<MobEffect, MobEffect> OUTPOST_RECOGNITION = EFFECTS.register(
            "outpost_recognition", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xC6A760) {});

    private ModDisguiseEffects() {}
}
