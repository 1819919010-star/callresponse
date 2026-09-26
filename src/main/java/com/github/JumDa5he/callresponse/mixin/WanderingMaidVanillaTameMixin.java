package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager;
import com.github.JumDa5he.callresponse.compat.wandering.WanderingMaidData;
import com.github.JumDa5he.callresponse.compat.trade.TradingMaidData;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Final gate for every route that eventually calls vanilla TamableAnimal.tame(Player). */
@Mixin(TamableAnimal.class)
public abstract class WanderingMaidVanillaTameMixin {
    @Inject(method = "tame", at = @At("HEAD"), cancellable = true)
    private void callresponse$blockVanillaTame(Player player, CallbackInfo ci) {
        Object self = this;
        if (self instanceof EntityMaid maid) {
            boolean blockedWandering = WanderingMaidData.isSpecial(maid) && !WanderingMaidData.mayAccept(maid);
            boolean blockedTrading = TradingMaidData.isTrading(maid) && !TradingMaidData.purchaseAuthorized(maid);
            if (blockedWandering || blockedTrading || BetrayalOutpostMaidData.isOutpostMaid(maid)
                    || EmotionBetrayalManager.isActualBetrayal(maid)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "setTame", at = @At("HEAD"), cancellable = true)
    private void callresponse$keepBetrayerUntamed(boolean tame, CallbackInfo ci) {
        Object self = this;
        if (tame && self instanceof EntityMaid maid && !maid.level().isClientSide
                && EmotionBetrayalManager.isActualBetrayal(maid)) ci.cancel();
    }

    @Inject(method = "setOwnerUUID", at = @At("HEAD"), cancellable = true)
    private void callresponse$keepBetrayerOwnerless(UUID ownerId, CallbackInfo ci) {
        Object self = this;
        if (ownerId != null && self instanceof EntityMaid maid && !maid.level().isClientSide
                && EmotionBetrayalManager.isActualBetrayal(maid)) ci.cancel();
    }
}
