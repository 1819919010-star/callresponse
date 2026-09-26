package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.outpost.OutpostRaiderDeathContext;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Raider.class)
public abstract class OutpostRaiderDeathMixin implements OutpostRaiderDeathContext {
    @Unique private Raid callresponse$raidBeforeDeath;

    @Inject(method = "die", at = @At("HEAD"))
    private void callresponse$captureRaidBeforeVanillaRemoval(DamageSource source, CallbackInfo ci) {
        callresponse$raidBeforeDeath = ((Raider) (Object) this).getCurrentRaid();
    }

    @Override
    public Raid callresponse$raidBeforeDeath() {
        return callresponse$raidBeforeDeath;
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void callresponse$clearCapturedRaid(DamageSource source, CallbackInfo ci) {
        callresponse$raidBeforeDeath = null;
    }
}
