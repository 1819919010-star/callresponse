package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.hunger.HungerAwareEdibleWrapper;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.CakeEdible;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(MaidEdibleBlockManager.class)
public class MixinMaidEdibleBlockManager {
    @Shadow
    private static List<IMaidEdibleBlock> EDIBLE_BLOCKS;

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList;copyOf(Ljava/util/Collection;)Lcom/google/common/collect/ImmutableList;"))
    private static void wrapCake(CallbackInfo ci) {
        EDIBLE_BLOCKS.replaceAll(food -> food.getClass() == CakeEdible.class
                ? new HungerAwareEdibleWrapper(food) : food);
    }
}
