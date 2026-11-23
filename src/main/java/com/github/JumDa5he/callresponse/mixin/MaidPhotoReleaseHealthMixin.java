package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.emotion.MaidReleaseHealth;
import com.github.tartaricacid.touhoulittlemaid.item.ItemPhoto;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemPhoto.class, remap = false)
public abstract class MaidPhotoReleaseHealthMixin {
    @ModifyArg(method = "lambda$useOn$1", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/Entity;load(Lnet/minecraft/nbt/CompoundTag;)V"), index = 0)
    private static CompoundTag callresponse$copyPhotoData(CompoundTag data) { return MaidReleaseHealth.copyForLoad(data); }

    @Redirect(method = "useOn", remap = true, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean callresponse$fillFirstPhotoRelease(Level level, Entity entity) {
        return MaidReleaseHealth.addReleased(level, entity);
    }
}
