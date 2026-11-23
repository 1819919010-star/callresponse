package com.github.JumDa5he.callresponse.compat.winefox;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.registries.BuiltInRegistries;

/** This mob's vanilla-only policy; optional mods retain their own effect behavior. */
public final class WineFoxVanillaEffects {
    private WineFoxVanillaEffects() {}

    public static boolean reject(Holder<MobEffect> effect) {
        var key = BuiltInRegistries.MOB_EFFECT.getKey(effect.value());
        if (key == null || !"minecraft".equals(key.getNamespace())) return false;
        // Explicitly cover Bad Omen regardless of its vanilla category.
        // Glowing is also NEUTRAL, but must remain available for pursuit leaders.
        return effect.value().getCategory() == MobEffectCategory.HARMFUL || effect.is(MobEffects.BAD_OMEN);
    }
}
