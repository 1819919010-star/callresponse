package com.github.tartaricacid.callresponse.mixin;

import com.github.tartaricacid.callresponse.CallResponseMod;
import com.github.tartaricacid.callresponse.compat.hunger.HungerAwareEdibleWrapper;
import com.github.tartaricacid.touhoulittlemaid.api.block.IMaidEdibleBlock;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.CakeEdible;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.MaidEdibleBlockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// 这里用 mixin 兼容性会更好
@Mixin(MaidEdibleBlockManager.class)
public class MixinMaidEdibleBlockManager {
    @Shadow
    private static List<IMaidEdibleBlock> EDIBLE_BLOCKS;

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList;copyOf(Ljava/util/Collection;)Lcom/google/common/collect/ImmutableList;"))
    private static void wrapBlocks(CallbackInfo ci){
        EDIBLE_BLOCKS.removeIf(item -> item instanceof CakeEdible);
        EDIBLE_BLOCKS.replaceAll(HungerAwareEdibleWrapper::new);
        CallResponseMod.LOGGER.info("✅ 已包装所有 MaidEdibleBlock（共 {} 个），饱食度同步已启用", EDIBLE_BLOCKS.size());
    }
}
