package com.github.JumDa5he.callresponse.mixin.revengecompat;

import com.github.JumDa5he.callresponse.compat.outpost.entity.RevengeMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.github.JumDa5he.moreanimation.compat.event.MaidInteractionEvent", remap=false)
public abstract class MoreAnimationInteractionMixin {
    @Inject(method="onDeath", at=@At("HEAD"), cancellable=true)
    private static void death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof RevengeMaidEntity) ci.cancel();
    }
    @Inject(method="requestInteraction(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;Ljava/lang/String;Lnet/minecraft/world/entity/Entity;)Z",
            at=@At("HEAD"), cancellable=true)
    private static void request(EntityMaid maid, String type, net.minecraft.world.entity.Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (maid instanceof RevengeMaidEntity || target instanceof RevengeMaidEntity) cir.setReturnValue(false);
    }
}
