package com.github.JumDa5he.callresponse.mixin.cuteactivity;

import com.github.JumDa5he.callresponse.compat.cuteactivity.CuteActivityBridge;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "cn.autoforged.maid_cute_activity.RaomingHandler", remap = false)
public abstract class CuteMercyPoseMixin {
    @Redirect(method = "onMaidTick", at = @At(value = "INVOKE",
            target = "Lcn/autoforged/maid_cute_activity/RaomingHandler;isBelowHealthPercent(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;I)Z"),
            require = 0, remap = false)
    private static boolean callresponse$excludeRevengeMercy(EntityMaid maid, int percent) {
        return !CuteActivityBridge.isRevengeMaid(maid) && maid.getMaxHealth() > 0
                && maid.getHealth() / maid.getMaxHealth() * 100.0F < percent;
    }
}
