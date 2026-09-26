package com.github.JumDa5he.callresponse.compat.hunt;

import net.minecraft.network.syncher.EntityDataAccessor;

/** 普通运行时接口，避免业务代码直接加载 Mixin 类。 */
public interface LivingEntityHealthAccess {
    EntityDataAccessor<Float> callresponse$getHealthAccessor();
}
