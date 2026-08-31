package com.github.JumDa5he.callresponse.mixin.accessor;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidMiscManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MaidMiscManager.class)
public interface EntityMaidTameInvoker {
    @Invoker(value = "tameMaid", remap = false)
    InteractionResult callresponse$invokeTameMaid(ItemStack stack, Player player);
}
