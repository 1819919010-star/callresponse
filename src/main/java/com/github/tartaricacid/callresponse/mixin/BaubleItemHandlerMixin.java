package com.github.tartaricacid.callresponse.mixin;

import com.github.tartaricacid.callresponse.compat.hunt.HuntDamageContext;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 狩猎目标保护破除：在狩猎伤害结算期间，跳过 TLM 饰品回调（onInjured 会把伤害取消掉）。
 * 只影响狩猎伤害瞬间（isSuppressing），平时完全正常。
 */
@Mixin(value = BaubleItemHandler.class, remap = false)
public abstract class BaubleItemHandlerMixin {
    @Inject(method = "fireEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$skipDuringHuntDamage(CallbackInfoReturnable<Boolean> cir) {
        if (HuntDamageContext.isSuppressingProtection()) {
            cir.setReturnValue(false);
        }
    }
}
