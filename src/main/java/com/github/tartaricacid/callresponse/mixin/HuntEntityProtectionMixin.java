package com.github.tartaricacid.callresponse.mixin;

import com.github.tartaricacid.callresponse.compat.hunt.HuntOrderManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在原版 Entity 层关闭当前狩猎来源的 invulnerable 判定。 */
@Mixin(value = Entity.class, priority = 4000)
public abstract class HuntEntityProtectionMixin {
    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void callresponse$allowHuntDamageSource(DamageSource source,
                                                     CallbackInfoReturnable<Boolean> cir) {
        Entity target = (Entity) (Object) this;
        if (HuntOrderManager.isHuntDamage(target, source)) {
            cir.setReturnValue(false);
        }
    }
}
