package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEffects;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;

/** Only replaces these bottles' marker effect; vanilla handles other effects, consumption and bottles. */
@Mixin(PotionItem.class)
public abstract class WineFoxPotionDrinkMixin {
    @Redirect(method = "finishUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/alchemy/PotionContents;forEachEffect(Ljava/util/function/Consumer;)V"))
    private void callresponse$drink(PotionContents contents, Consumer<MobEffectInstance> nativeEffect,
                                   ItemStack stack, Level level, LivingEntity drinker) {
        if (!WineFoxPotions.isCustomPotion(stack)) {
            contents.forEachEffect(nativeEffect);
            return;
        }
        boolean[] applied = {false};
        contents.forEachEffect(effect -> {
            boolean marker = WineFoxPotions.hasBasePotion(contents, WineFoxPotions.MALICE) && effect.is(WineFoxEffects.CORRUPTED_HUNT)
                    || WineFoxPotions.hasBasePotion(contents, WineFoxPotions.DESSERT) && effect.is(WineFoxEffects.SWEETNESS);
            if (!marker) nativeEffect.accept(effect);
            else if (!applied[0]) {
                applied[0] = true;
                WineFoxPotions.applyDrink(stack, drinker);
            }
        });
    }
}
