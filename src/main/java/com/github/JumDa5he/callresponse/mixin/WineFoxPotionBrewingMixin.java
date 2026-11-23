package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Both new recipes produce drinkable bottles only; unrelated mixes remain native. */
@Mixin(PotionBrewing.class)
public abstract class WineFoxPotionBrewingMixin {
    @Inject(method = "hasMix", at = @At("HEAD"), cancellable = true)
    private void callresponse$drinkOnly(ItemStack input, ItemStack reagent, CallbackInfoReturnable<Boolean> ci) {
        if (WineFoxPotions.isCustomPotion(input) && reagent.is(Items.GUNPOWDER)) ci.setReturnValue(false);
    }
    @Inject(method = "mix", at = @At("HEAD"), cancellable = true)
    private void callresponse$noSplash(ItemStack reagent, ItemStack input, CallbackInfoReturnable<ItemStack> ci) {
        if (WineFoxPotions.isCustomPotion(input) && reagent.is(Items.GUNPOWDER)) ci.setReturnValue(input);
    }
}
