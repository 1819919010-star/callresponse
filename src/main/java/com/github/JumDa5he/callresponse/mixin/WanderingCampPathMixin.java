package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class WanderingCampPathMixin {
    @Inject(method="getMaxFallDistance", at=@At("HEAD"), cancellable=true)
    private void callresponse$smallFall(CallbackInfoReturnable<Integer> cir) {
        if ((Object)this instanceof EntityMaid maid && WanderingCampData.active(maid)) cir.setReturnValue(2);
    }
    @Inject(method="getPathfindingMalus", at=@At("HEAD"), cancellable=true)
    private void callresponse$escapeStart(BlockPathTypes type, CallbackInfoReturnable<Float> cir) {
        if ((Object)this instanceof EntityMaid maid && WanderingCampData.active(maid)
                && maid.level().getGameTime() < WanderingCampData.data(maid).getLong("PanicUntil")
                && (WanderingCampBrain.hazard(maid,maid.blockPosition()) || WanderingCampBrain.hazard(maid,maid.blockPosition().below()))
                && (type == BlockPathTypes.DAMAGE_FIRE || type == BlockPathTypes.DAMAGE_OTHER || type == BlockPathTypes.LAVA))
            cir.setReturnValue(8F); // permit leaving a harmful start node, never a harmful destination/path
    }
}
