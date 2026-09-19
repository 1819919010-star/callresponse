package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.facility.MaidBedPlayerRestManager;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 女仆床不是原版 BedBlock，显式提供玩家躺卧时的床头方向。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMaidBedOrientationMixin {
    @Inject(method = "getBedOrientation", at = @At("HEAD"), cancellable = true)
    private void callresponse$maidBedOrientation(CallbackInfoReturnable<Direction> cir) {
        Direction direction = MaidBedPlayerRestManager.bedDirection((LivingEntity) (Object) this);
        if (direction != null) cir.setReturnValue(direction);
    }
}
