package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.damage.OwnerDamageSource;
import com.github.JumDa5he.callresponse.compat.damage.OwnerGunExplosionProtection;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Superb Warfare 的实体命中事件同样早于女仆原生 hurt 管线。
 * 参数使用 Object + @Coerce，确保未安装该枪械模组时不会产生硬依赖。
 */
@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.compat.gun.swarfare.event.GunHurtMaidEvent",
        priority = 4000, remap = false)
public abstract class SuperbWarfareOwnerGunProtectionMixin {
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
