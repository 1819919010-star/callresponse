package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 魂符序列化女仆前先解除公主抱，避免把乘客关系写进魂符并随 carrier 一起移除。 */
@Mixin(value = ItemSmartSlab.class, remap = false)
public abstract class PrincessCarrySoulSlabMixin {
    @Inject(method = "storeMaidData", at = @At("HEAD"))
    private static void callresponse$releasePrincessCarryBeforeSave(ItemStack stack, EntityMaid maid,
                                                                    CallbackInfo ci) {
        PrincessCarryManager.releaseBeforeStorage(maid);
    }
}
