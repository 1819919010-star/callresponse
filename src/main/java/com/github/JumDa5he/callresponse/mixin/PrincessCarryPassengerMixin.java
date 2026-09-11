package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将乘客稳定放在女仆怀前，计算方式沿用 TLM 玩家抱女仆的 positionRider 实现。 */
@Mixin(Entity.class)
public abstract class PrincessCarryPassengerMixin {
    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("HEAD"), cancellable = true)
    private void callresponse$positionPrincessCarryPassenger(Entity passenger, Entity.MoveFunction callback,
                                                              CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof EntityMaid carrier) || !(passenger instanceof EntityMaid carried)
                || !PrincessCarryManager.isMaidCarrySession(carrier, carried)) {
            return;
        }
        Vec3 position = carrier.position();
        float radians = (float) -Math.toRadians(carrier.yBodyRot);
        Vec3 offset = position.add(new Vec3(0.0D, 0.0D, 0.75D).yRot(radians));
        callback.accept(passenger, offset.x(), offset.y() + 0.15D, offset.z());
        ci.cancel();
    }
}
