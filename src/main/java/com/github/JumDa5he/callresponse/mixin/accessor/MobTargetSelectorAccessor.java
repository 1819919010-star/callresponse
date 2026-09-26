package com.github.JumDa5he.callresponse.mixin.accessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobTargetSelectorAccessor {
    @Accessor("targetSelector")
    GoalSelector callresponse$getTargetSelector();

    @Accessor("goalSelector")
    GoalSelector callresponse$getGoalSelector();
}
