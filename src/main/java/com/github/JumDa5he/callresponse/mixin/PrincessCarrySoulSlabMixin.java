package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.AbstractStoreMaidItem;
import com.github.JumDa5he.callresponse.compat.emotion.MaidReleaseHealth;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 魂符序列化女仆前先解除公主抱，避免把乘客关系写进魂符并随 carrier 一起移除。 */
@Mixin(value = AbstractStoreMaidItem.class, remap = false)
public abstract class PrincessCarrySoulSlabMixin {
    @Inject(method = "storeMaidData", at = @At("HEAD"))
    private static void callresponse$releasePrincessCarryBeforeSave(ItemStack stack, EntityMaid maid,
                                                                    CallbackInfo ci) {
        com.github.JumDa5he.callresponse.compat.playercarry.PlayerCarryManager.releaseHolder(maid);
        PrincessCarryManager.releaseBeforeStorage(maid);
    }

    @WrapOperation(method = "spawnFromStore", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean callresponse$insertReleased(Level level, Entity entity, Operation<Boolean> original,
                                                    @Cancellable CallbackInfoReturnable<InteractionResult> callback) {
        if (!((Object) this instanceof com.github.tartaricacid.touhoulittlemaid.item.ItemPhoto)
                && !((Object) this instanceof com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab))
            return original.call(level, entity);
        boolean added = MaidReleaseHealth.addReleased(level, entity, () -> original.call(level, entity));
        if (!added) callback.setReturnValue(InteractionResult.FAIL);
        return added;
    }
}
