package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.hunger.HungerManager;
import com.github.JumDa5he.callresponse.compat.state.MaidMovementControl;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidAwaitTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MaidAwaitTask.class)
public abstract class MaidAwaitTaskMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void callresponse$keepControlledPath(ServerLevel level, EntityMaid maid, long gameTime,
                                                  CallbackInfo ci) {
        if (MaidMovementControl.controlsPath(maid) || HungerManager.isBeggingForFood(maid)) {
            ci.cancel();
        }
    }
}
