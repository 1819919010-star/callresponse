package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.task.PrincessCarryManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemSmartSlab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 魂符序列化女仆前先解除公主抱，避免把乘客关系写进魂符并随 carrier 一起移除。 */
@Mixin(value = ItemSmartSlab.class, remap = false)
public abstract class PrincessCarrySoulSlabMixin {
    @Inject(method = "storeMaidData", at = @At("HEAD"))
    private static void callresponse$releasePrincessCarryBeforeSave(ItemStack stack, EntityMaid maid,
                                                                    CallbackInfo ci) {
        com.github.JumDa5he.callresponse.compat.playercarry.PlayerCarryManager.releaseHolder(maid);
        PrincessCarryManager.releaseBeforeStorage(maid);
    }

    @ModifyArg(method = "spawnFromStore", at = @At(value = "INVOKE", remap = true,
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;load(Lnet/minecraft/nbt/CompoundTag;)V"), index = 0)
    private net.minecraft.nbt.CompoundTag callresponse$copyStoredMaid(net.minecraft.nbt.CompoundTag data) {
        return com.github.JumDa5he.callresponse.compat.emotion.MaidReleaseHealth.copyForLoad(data);
    }

    // INIT's spawnNewMaid places the entity in this exact capability lambda in TLM 1.5.3.
    @Redirect(method = {"spawnFromStore", "lambda$spawnNewMaid$0"}, require = 2,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean callresponse$fillFirstSlabRelease(net.minecraft.world.level.Level level,
                                                      net.minecraft.world.entity.Entity entity) {
        return com.github.JumDa5he.callresponse.compat.emotion.MaidReleaseHealth.addReleased(level, entity);
    }
}
