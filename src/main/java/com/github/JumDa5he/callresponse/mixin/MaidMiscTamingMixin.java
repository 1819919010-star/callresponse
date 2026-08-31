package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.trade.TradingMaidData;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidMiscManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.1 将原版驯服入口移入 MaidMiscManager；特殊女仆只能走附属授权流程。 */
@Mixin(MaidMiscManager.class)
public abstract class MaidMiscTamingMixin {
    @Shadow
    @Final
    private EntityMaid maid;

    @Inject(method = "tameMaid", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$rejectUnauthorizedTaming(ItemStack stack, Player player,
                                                       CallbackInfoReturnable<InteractionResult> cir) {
        if ((WanderingMaidData.isSpecial(maid) && !WanderingMaidData.mayAccept(maid))
                || (TradingMaidData.isTrading(maid) && !TradingMaidData.purchaseAuthorized(maid))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
