package com.github.JumDa5he.callresponse.mixin.cuteactivity.client;

import com.github.JumDa5he.callresponse.compat.cuteactivity.client.CuteActivityScareClient;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 合并原版受击 scare 与威压 scare，不改合作模组计时。 */
@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.ScareAnimClient", remap = false)
public abstract class CuteScareClientMixin {
    @Inject(method = "isScareActive", at = @At("RETURN"), cancellable = true, remap = false)
    private static void callresponse$combineScareSources(EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        if (CuteActivityScareClient.isRevenge(maid)) cir.setReturnValue(false);
        else if (!cir.getReturnValue() && CuteActivityScareClient.isIntimidationScareActive(maid))
            cir.setReturnValue(true);
    }
}
