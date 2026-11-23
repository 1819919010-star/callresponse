package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.emotion.MaidReleaseHealth;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.github.tartaricacid.touhoulittlemaid.data.MaidNumAttachment;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataAttachment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemSmartSlab.class, remap = false)
public abstract class MaidInitSlabReleaseMixin {
    @WrapOperation(method = "spawnNewMaid", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/data/MaidNumAttachment;add()V"))
    private void callresponse$reserveMaidSlot(MaidNumAttachment count, Operation<Void> original,
                                            @Share("callresponse$reserved") LocalBooleanRef reserved) {
        original.call(count);
        reserved.set(true);
    }

    @WrapOperation(method = "spawnNewMaid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean callresponse$insertReleased(Level level, Entity entity, Operation<Boolean> original,
            @Cancellable CallbackInfoReturnable<InteractionResult> callback,
            @Local(argsOnly = true) Player player,
            @Share("callresponse$reserved") LocalBooleanRef reserved) {
        boolean added = false;
        try {
            added = MaidReleaseHealth.addReleased(level, entity, () -> original.call(level, entity));
            if (!added) callback.setReturnValue(InteractionResult.FAIL);
            return added;
        } finally {
            // TLM reserves one survival-mode maid slot before trying the actual world join.
            if (!added && reserved.get()) player.getData(InitDataAttachment.MAID_NUM).min(1);
        }
    }
}
