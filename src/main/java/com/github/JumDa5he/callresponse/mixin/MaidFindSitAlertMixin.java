package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostAlertManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFindSitTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 战斗压力存在时，据点女仆不会开始新的 EntitySit 娱乐行为。 */
@Mixin(MaidFindSitTask.class)
public abstract class MaidFindSitAlertMixin {
    @Inject(method = "checkExtraStartConditions", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$combatBeforeSit(ServerLevel level, EntityMaid maid,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (BetrayalOutpostAlertManager.shouldBlockLeisure(maid)) cir.setReturnValue(false);
    }
}
