package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.OutpostMaidTargetGoal;
import com.github.JumDa5he.callresponse.compat.outpost.OutpostRaidAdvanceGoal;
import com.github.JumDa5he.callresponse.mixin.accessor.MobTargetSelectorAccessor;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 Raider 原有目标选择器中增加一个只认当前营地 Raid 的条件目标。 */
@Mixin(Raider.class)
public abstract class OutpostRaiderTargetMixin {
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void callresponse$addCampMaidTarget(CallbackInfo ci) {
        Raider raider = (Raider) (Object) this;
        MobTargetSelectorAccessor goals = (MobTargetSelectorAccessor) raider;
        goals.callresponse$getTargetSelector().addGoal(0, new OutpostMaidTargetGoal(raider));
        goals.callresponse$getGoalSelector().addGoal(2, new OutpostRaidAdvanceGoal(raider));
    }
}
