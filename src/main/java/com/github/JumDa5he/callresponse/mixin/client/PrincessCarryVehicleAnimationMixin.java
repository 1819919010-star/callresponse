package com.github.JumDa5he.callresponse.mixin.client;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.condition.ConditionalVehicle;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 让被女仆抱着的女仆复用 TLM 原版“被玩家抱起”的 vehicle 动画入口。 */
@Mixin(value = ConditionalVehicle.class, remap = false)
public abstract class PrincessCarryVehicleAnimationMixin {
    @Inject(method = "doTest", at = @At("HEAD"), cancellable = true)
    private void callresponse$usePlayerCarryAnimation(Mob mob, CallbackInfoReturnable<String> cir) {
        if (mob instanceof EntityMaid carried && carried.getVehicle() instanceof EntityMaid carrier
                && PrincessCarryManager.isMaidCarrySession(carrier, carried)) {
            cir.setReturnValue("vehicle$minecraft:player");
        }
    }
}
