package com.github.JumDa5he.callresponse.compat.winefox;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.registries.ForgeRegistries;

/** This mob's vanilla-only policy; optional mods retain their own effect behavior. */
public final class WineFoxVanillaEffects {
    private WineFoxVanillaEffects() {}

    public static boolean reject(MobEffect effect) {
        var key = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        if (key == null || !"minecraft".equals(key.getNamespace())) return false;
        // Bad Omen is NEUTRAL in 1.20.1, despite its adverse raid behavior.
        // Glowing is also NEUTRAL, but must remain available for pursuit leaders.
        return effect.getCategory() == MobEffectCategory.HARMFUL || effect == MobEffects.BAD_OMEN;
    }
}
