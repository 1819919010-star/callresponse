package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.OutpostRaidManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 原版创建流程照常运行，仅本次营地触发将 POI 中心替换为结构中心。 */
@Mixin(Raids.class)
public abstract class OutpostRaidsCenterMixin {
    @Inject(method = "createOrExtendRaid", at = @At("HEAD"))
    private void callresponse$beginOutpostRaid(ServerPlayer player, BlockPos pos,
                                               CallbackInfoReturnable<Raid> cir) {
        OutpostRaidManager.beginRaidCreation(player, pos);
    }

    @ModifyArg(method = "createOrExtendRaid",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raids;getOrCreateRaid(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/entity/raid/Raid;"),
            index = 1)
    private BlockPos callresponse$useOutpostCenter(BlockPos vanillaCenter) {
        return OutpostRaidManager.raidCenterForCreation(vanillaCenter);
    }

    @Inject(method = "createOrExtendRaid", at = @At("RETURN"))
    private void callresponse$finishOutpostRaid(ServerPlayer player, BlockPos pos,
                                                CallbackInfoReturnable<Raid> cir) {
        OutpostRaidManager.finishRaidCreation(player, cir.getReturnValue());
    }
}
