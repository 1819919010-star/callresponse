package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.migration.MigrationNbtBridge;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import io.github.zgxhzhr.maidfm.data.MaidFileData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.zgxhzhr.maidfm.service.MaidTransferService", remap = false)
public abstract class MaidFileManagerNbtMixin {
    @Inject(method = "doSerializeMaid", at = @At("RETURN"), remap = false)
    private static void callresponse$portableOwnNbt(EntityMaid maid, CallbackInfoReturnable<MaidFileData> cir) {
        var result = cir.getReturnValue();
        if (result != null && result.getData() != null)
            result.setData(MigrationNbtBridge.exportCopy(result.getData()));
    }
    @Inject(method = "loadMaidNbt", at = @At("HEAD"), remap = false)
    private static void callresponse$importOwnNbt(EntityMaid maid, CompoundTag migrated, CompoundTag tlmTag,
            boolean keepBaubles, boolean lightning, CallbackInfo ci) {
        MigrationNbtBridge.prepareImport(migrated);
    }
}
