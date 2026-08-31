package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.api.event.hunger.MaidEatEvent;
import com.github.JumDa5he.callresponse.compat.hunger.HungerData;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.edible.CakeEdible;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// 用 mixin 可以照护到子类
@Mixin(CakeEdible.class)
public class MixinCakeEdible {
    @Inject(method = "consume", at = @At("RETURN"))
    public void addHunger(EntityMaid maid, BlockPos pos, BlockState state, CallbackInfoReturnable<Boolean> cir){
        HungerData.add(maid, NeoForge.EVENT_BUS.post(new MaidEatEvent.Block(maid, pos, state, 6)).getHunger());
    }
}
