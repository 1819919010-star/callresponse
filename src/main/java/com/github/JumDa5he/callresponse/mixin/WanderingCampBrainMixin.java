package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.*;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidBrain;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MaidBrain.class)
public abstract class WanderingCampBrainMixin {
    @Inject(method="registerBrainGoals", at=@At("HEAD"), cancellable=true, remap=false)
    private static void callresponse$campOnly(Brain<EntityMaid> brain, EntityMaid maid, CallbackInfo ci) {
        if (CampRoutineProfile.bound(maid).isPresent()) {
            WanderingBehaviorAuthority.bind(brain, maid);
            WanderingCampBrain.register(brain);
            ci.cancel();
        }
    }
}
