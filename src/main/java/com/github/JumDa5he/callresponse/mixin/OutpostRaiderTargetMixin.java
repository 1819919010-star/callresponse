package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.OutpostRaidAdvanceGoal;
import com.github.JumDa5he.callresponse.mixin.accessor.MobTargetSelectorAccessor;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raider movement supplement; the shared hostile target is registered once on entity join. */
@Mixin(Raider.class)
public abstract class OutpostRaiderTargetMixin {
    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void callresponse$addCampAdvance(CallbackInfo ci) {
        Raider raider = (Raider) (Object) this;
        MobTargetSelectorAccessor goals = (MobTargetSelectorAccessor) raider;
        goals.callresponse$getGoalSelector().addGoal(2, new OutpostRaidAdvanceGoal(raider));
    }
}
