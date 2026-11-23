package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.wanderingcamp.*;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(TamableAnimal.class)
public abstract class WanderingCampTameMixin {
    private boolean callresponse$blocked() {
        return (Object)this instanceof EntityMaid maid
                && (WanderingCampData.active(maid) && !((CampMaid)maid).callresponse$campTaming()
                    || com.github.JumDa5he.callresponse.compat.emotion.EmotionBetrayalManager.isActualBetrayal(maid));
    }
    @Inject(method="tame", at=@At("HEAD"), cancellable=true)
    private void callresponse$gate(Player player, CallbackInfo ci) { if (callresponse$blocked()) ci.cancel(); }
    @Inject(method="setTame", at=@At("HEAD"), cancellable=true)
    private void callresponse$gateTame(boolean tame, boolean applyAttributes, CallbackInfo ci) { if (tame && callresponse$blocked()) ci.cancel(); }
    @Inject(method="setOwnerUUID", at=@At("HEAD"), cancellable=true)
    private void callresponse$gateOwner(UUID owner, CallbackInfo ci) { if (owner != null && callresponse$blocked()) ci.cancel(); }
}
