package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidManager;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(EntityMaid.class)
public abstract class WanderingMaidInteractionMixin {
    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void callresponse$blockWanderingMaidInteraction(Player player, InteractionHand hand,
                                                             CallbackInfoReturnable<InteractionResult> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (WanderingMaidManager.blocksNormalInteraction(maid)) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }


    @Inject(method = "tameMaid", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$rejectOriginalTaming(ItemStack stack, Player player,
                                                    CallbackInfoReturnable<InteractionResult> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (WanderingMaidData.isSpecial(maid) && !WanderingMaidData.mayAccept(maid)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
