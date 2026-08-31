package com.github.JumDa5he.callresponse.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityHealthAccessorMixin {
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> callresponse$getHealthAccessor() {
        throw new AssertionError();
    }
}
