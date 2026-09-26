package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.OutpostRaidMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 仅在已标记的营地 Raid 自身 tick 中接受营地中心，绝不修改 ServerLevel 全局村庄判断。 */
@Mixin(Raid.class)
public abstract class OutpostRaidVillageCheckMixin implements OutpostRaidMarker {
    @Unique private boolean callresponse$outpostRaid;

    @Override
    public boolean callresponse$isOutpostRaid() {
        return callresponse$outpostRaid;
    }

    @Override
    public void callresponse$setOutpostRaid(boolean value) {
        callresponse$outpostRaid = value;
    }

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void callresponse$readOutpostMarker(ServerLevel level, CompoundTag tag, CallbackInfo ci) {
        callresponse$outpostRaid = tag.getBoolean("CallResponseOutpostRaid");
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void callresponse$saveOutpostMarker(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (callresponse$outpostRaid) cir.getReturnValue().putBoolean("CallResponseOutpostRaid", true);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isVillage(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean callresponse$allowOutpostRaidCenter(ServerLevel level, BlockPos pos) {
        return level.isVillage(pos) || (callresponse$outpostRaid && ((Raid) (Object) this).getCenter().equals(pos));
    }

    @Redirect(method = "updateRaiders", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isVillage(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean callresponse$keepCampRaidersInWave(ServerLevel level, BlockPos pos) {
        if (level.isVillage(pos)) return true;
        if (!callresponse$outpostRaid) return false;
        BlockPos center = ((Raid) (Object) this).getCenter();
        long dx = (long) pos.getX() - center.getX();
        long dz = (long) pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= 48L * 48L;
    }
}
