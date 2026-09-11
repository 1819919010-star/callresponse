package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.facility.MaidBedPlayerRestManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 防止原版姿势更新把受管理的纯躺卧状态立即改回站立。 */
@Mixin(Player.class)
public abstract class PlayerRestPoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void callresponse$keepMaidBedRestPose(CallbackInfo ci) {
        if (MaidBedPlayerRestManager.shouldKeepRestingPose((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
