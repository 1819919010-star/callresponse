package com.github.tartaricacid.callresponse.mixin;

import com.github.tartaricacid.callresponse.compat.hunger.HungerData;
import com.github.tartaricacid.callresponse.compat.hunger.HungerManager;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.CakeEdible;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 用 mixin 可以照护到子类
@Mixin(CakeEdible.class)
public class MixinCakeEdible {
    @Inject(method = "consume", at = @At("RETURN"))
    public void addHunger(EntityMaid maid, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir){
        HungerData.add(maid, 6.0f);
    }
}
