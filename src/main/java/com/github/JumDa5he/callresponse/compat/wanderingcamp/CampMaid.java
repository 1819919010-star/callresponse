package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import net.minecraft.nbt.CompoundTag;

/** Only presentation metadata is synchronized; invitation outcome remains server-private. */
public interface CampMaid {
    boolean callresponse$campDeathCommitted();
    CompoundTag callresponse$campView();
    void callresponse$campView(CompoundTag tag);
    boolean callresponse$campTaming();
    void callresponse$campTaming(boolean authorized);
    java.util.UUID callresponse$campDamageAttacker();
    boolean callresponse$isCampViewKey(net.minecraft.network.syncher.EntityDataAccessor<?> key);
}
