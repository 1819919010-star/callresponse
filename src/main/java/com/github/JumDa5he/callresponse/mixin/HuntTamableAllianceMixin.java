package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.hunt.HuntOrderManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 当前明确的狩猎目标不再被驯服生物的主人/队伍逻辑视为友军。 */
@Mixin(value = Entity.class, priority = 4000)
public abstract class HuntTamableAllianceMixin {
    @Inject(method = "isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void callresponse$treatHuntTargetAsEnemy(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof EntityMaid maid
                && !maid.level().isClientSide()
                && HuntOrderManager.isHuntTarget(maid, target)) {
            cir.setReturnValue(false);
        }
    }
}