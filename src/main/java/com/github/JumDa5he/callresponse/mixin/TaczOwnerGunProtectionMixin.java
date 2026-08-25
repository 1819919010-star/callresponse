package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageSource;
import com.github.JumDa5he.callresponse.compat.damage.OwnerGunExplosionProtection;
import net.minecraftforge.event.level.ExplosionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ 会在枪械伤害进入 EntityMaid.hurt 前，由 TLM 兼容层取消友方命中。
 * 这里只跳过“当前主人打自己的女仆”这一条 TLM 保护监听器，不取消枪械事件本身。
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.compat.gun.tacz.event.GunHurtMaidEvent",
        priority = 4000, remap = false)
public abstract class TaczOwnerGunProtectionMixin {
    @Inject(method = "onGunHurt", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void callresponse$allowOwnerGunHit(@Coerce Object event, CallbackInfo ci) {
        if (OwnerDamageSource.belongsToOwnerDamageEvent(event)) {
            ci.cancel();
        }
    }

    @Inject(method = "onExplosionDetonateEvent", at = @At("HEAD"), cancellable = true,
            require = 0, remap = false)
    private void callresponse$filterExplosionProtection(ExplosionEvent.Detonate event, CallbackInfo ci) {
        if (OwnerGunExplosionProtection.filterProtectedMaids(event)) {
            ci.cancel();
        }
    }
}
