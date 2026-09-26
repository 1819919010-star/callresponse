package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 Entity 真正从世界移除前解除公主抱，作为事件之外的最终安全兜底。 */
@Mixin(Entity.class)
public abstract class PrincessCarryRemovalMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void callresponse$releasePrincessCarryBeforeRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self instanceof EntityMaid maid && !maid.level().isClientSide) {
            PrincessCarryManager.releaseBeforeRemoval(maid);
        }
    }
}
