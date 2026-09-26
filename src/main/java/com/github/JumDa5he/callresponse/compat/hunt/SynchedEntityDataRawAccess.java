package com.github.JumDa5he.callresponse.compat.hunt;

import net.minecraft.network.syncher.SynchedEntityData;

/** SynchedEntityData 的原始数据访问桥，接口本身不是 Mixin。 */
public interface SynchedEntityDataRawAccess {
    SynchedEntityData.DataItem<?>[] callresponse$getItemsById();

    void callresponse$setDirty(boolean dirty);
}
