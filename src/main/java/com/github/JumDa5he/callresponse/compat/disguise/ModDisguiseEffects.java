package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModDisguiseEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
            ForgeRegistries.MOB_EFFECTS, CallResponseMod.MOD_ID);
    public static final RegistryObject<MobEffect> OUTPOST_RECOGNITION = EFFECTS.register(
            "outpost_recognition", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xC6A760) {});

    private ModDisguiseEffects() {}
}
