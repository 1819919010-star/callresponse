package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.OutpostRaidAdvanceGoal;
import com.github.JumDa5he.callresponse.mixin.accessor.MobTargetSelectorAccessor;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 保留 Raider 原导航器，仅追加特殊营地 Raid 的无目标推进 Goal。 */
@Mixin(Raider.class)
public abstract class OutpostRaiderTargetMixin {
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void callresponse$addCampMaidTarget(CallbackInfo ci) {
        Raider raider = (Raider) (Object) this;
        MobTargetSelectorAccessor goals = (MobTargetSelectorAccessor) raider;
        if (goals.callresponse$getGoalSelector().getAvailableGoals().stream()
                .noneMatch(goal -> goal.getGoal() instanceof OutpostRaidAdvanceGoal)) {
            goals.callresponse$getGoalSelector().addGoal(2, new OutpostRaidAdvanceGoal(raider));
        }
    }
}
