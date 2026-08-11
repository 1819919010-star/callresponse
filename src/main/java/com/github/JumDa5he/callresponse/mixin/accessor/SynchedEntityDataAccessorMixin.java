package com.github.JumDa5he.callresponse.mixin.accessor;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessorMixin {
    @Accessor("itemsById")
    Int2ObjectMap<SynchedEntityData.DataItem<?>> callresponse$getItemsById();

    @Accessor("isDirty")
    void callresponse$setDirty(boolean dirty);
}
