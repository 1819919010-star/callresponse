package com.github.JumDa5he.callresponse.mixin.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Avoid Configured 2.2.x parsing Forge comments when our localized tooltip is already complete. */
@Pseudo
@Mixin(targets = "com.mrcrayfish.configured.impl.forge.ForgeValue", remap = false)
public abstract class ConfiguredForgeValueMixin {
    @Shadow(remap = false)
    public abstract String getTranslationKey();

    @Inject(method = "getComment", at = @At("HEAD"), cancellable = true, remap = false)
    private void callresponse$useLocalizedTooltip(CallbackInfoReturnable<Component> callback) {
        String translationKey = getTranslationKey();
        if (translationKey == null || !translationKey.startsWith("callresponse.configuration.")) return;

        String tooltipKey = translationKey + ".tooltip";
        if (I18n.exists(tooltipKey)) {
            callback.setReturnValue(Component.translatable(tooltipKey));
        }
    }
}
