package com.github.JumDa5he.callresponse.mixin.accessor;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityMaid.class)
public interface EntityMaidHandItemInvoker {
    @Invoker(value = "backCurrentHandItemStack", remap = false)
    void callresponse$restoreTemporaryHandItem();
}
