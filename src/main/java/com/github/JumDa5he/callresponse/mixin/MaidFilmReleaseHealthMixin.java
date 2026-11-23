package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.emotion.MaidReleaseHealth;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.github.tartaricacid.touhoulittlemaid.item.ItemFilm;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemFilm.class, remap = false)
public abstract class MaidFilmReleaseHealthMixin {
    @WrapOperation(method = "filmToMaid", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"))
    private static void callresponse$readFilm(EntityMaid maid, CompoundTag tag, Operation<Void> original) {
        MaidReleaseHealth.readFilmRelationships(maid, tag);
        original.call(maid, tag.copy());
        MaidReleaseHealth.readMarker(maid, tag);
    }

    @WrapOperation(method = "filmToMaid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean callresponse$insertReleased(Level level, Entity entity, Operation<Boolean> original,
                                                    @Cancellable CallbackInfo callback) {
        boolean added = MaidReleaseHealth.addReleased(level, entity, () -> original.call(level, entity));
        if (!added) callback.cancel();
        return added;
    }
}
