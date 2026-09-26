package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.game.BoardGameManager;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TLM 的 EntitySit 会按日程检查娱乐资格并每秒弹下不合条件的女仆。
 * 围观席由 BoardGameManager 独立管理，因此只对已登记的真实围观席跳过该检查。
 */
@Mixin(EntitySit.class)
public abstract class EntitySitSpectatorMixin {
    @Inject(method = "tickMaid", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$keepBoardSpectatorSeated(EntityMaid maid, CallbackInfo ci) {
        EntitySit seat = (EntitySit) (Object) this;
        if (!BoardGameManager.isActiveSpectatorSeat(seat, maid)) return;
        maid.setYRot(seat.getYRot());
        maid.setYHeadRot(seat.getYRot());
        ci.cancel();
    }
}
