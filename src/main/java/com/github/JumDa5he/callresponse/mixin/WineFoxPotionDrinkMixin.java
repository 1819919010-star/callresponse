package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEffects;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces only these bottles' base effect; native consumption, statistics and bottle return remain. */
@Mixin(PotionItem.class)
public abstract class WineFoxPotionDrinkMixin {
    @Unique private static final ThreadLocal<Boolean> callresponse$applied = new ThreadLocal<>();
    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void callresponse$begin(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> ci) {
        callresponse$applied.set(false);
    }
    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void callresponse$end(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> ci) {
        callresponse$applied.remove();
    }
    @Redirect(method = "finishUsingItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean callresponse$drink(LivingEntity target, MobEffectInstance effect,
                                      ItemStack stack, Level level, LivingEntity drinker) {
        var potion = PotionUtils.getPotion(stack);
        if (WineFoxPotions.isCustomPotion(stack)
                && (potion == WineFoxPotions.MALICE.get() && effect.getEffect() == WineFoxEffects.CORRUPTED_HUNT.get()
                || potion == WineFoxPotions.DESSERT.get() && effect.getEffect() == WineFoxEffects.SWEETNESS.get())) {
            if (Boolean.TRUE.equals(callresponse$applied.get())) return false;
            callresponse$applied.set(true);
            try { return WineFoxPotions.applyDrink(stack, target); }
            finally { callresponse$applied.set(true); }
        }
        return target.addEffect(effect);
    }
}
