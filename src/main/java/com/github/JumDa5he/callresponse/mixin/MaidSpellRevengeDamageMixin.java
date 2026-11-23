package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostAlertManager;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostMaidData;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.CampPolicy;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import java.util.function.BiFunction;

/** Wrap only the verified blanket player/maid identity rule, never undo damage cancellation. */
@Pseudo
@Mixin(targets = "com.github.yimeng261.maidspell.Global", remap = false)
public abstract class MaidSpellRevengeDamageMixin {
    @Shadow @Final public static List<BiFunction<LivingHurtEvent, EntityMaid, Void>> commonHurtHandlers;

    @Inject(method = "resetCommonDamageCalc()V", at = @At("TAIL"))
    private static void callresponse$allowHostilePlayerDamage(CallbackInfo ci) {
        var identityRule = commonHurtHandlers.get(0);
        commonHurtHandlers.set(0, (event, maid) -> {
            if (!maid.level().isClientSide && maid.isAlive()
                    && event.getEntity() instanceof Player player && event.getSource().getEntity() == maid
                    && BetrayalOutpostMaidData.isOutpostMaid(maid) && !CampPolicy.nonCombat(maid)
                    && BetrayalOutpostAlertManager.isBaseValidTarget(maid, player)) return null;
            return identityRule.apply(event, maid);
        });
    }
}
