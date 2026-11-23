package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.emotion.MaidReleaseHealth;
import com.github.tartaricacid.touhoulittlemaid.item.ItemFilm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemFilm.class, remap = false)
public abstract class MaidFilmReleaseHealthMixin {
    @ModifyArg(method = "filmToMaid", at = @At(value = "INVOKE", remap = true,
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V"), index = 0)
    private static CompoundTag callresponse$copyFilmData(CompoundTag data) { return MaidReleaseHealth.copyForLoad(data); }

    @Redirect(method = "filmToMaid", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean callresponse$fillFirstFilmRelease(Level level, Entity entity) {
        return MaidReleaseHealth.addReleased(level, entity);
    }
}
