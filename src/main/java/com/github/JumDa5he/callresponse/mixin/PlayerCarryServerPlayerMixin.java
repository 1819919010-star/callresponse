package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.playercarry.PlayerCarryManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class PlayerCarryServerPlayerMixin {
    @Inject(method = "setPlayerInput", at = @At("HEAD"), cancellable = true)
    private void callresponse$passivePlayer(float strafe, float forward, boolean jumping, boolean sneaking, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        if (!PlayerCarryManager.isHeld(player)) return;
        player.xxa = player.zza = 0;
        player.setJumping(false);
        player.setShiftKeyDown(sneaking);
        ci.cancel();
    }
    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void callresponse$doNotPersistHeldVehicle(CompoundTag data, CallbackInfo ci) {
        if (PlayerCarryManager.isHeld((ServerPlayer)(Object)this)) data.remove("RootVehicle");
    }
}
