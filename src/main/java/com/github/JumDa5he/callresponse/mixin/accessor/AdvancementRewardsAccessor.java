package com.github.JumDa5he.callresponse.mixin.accessor;

import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CommandFunction;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AdvancementRewards.class)
public interface AdvancementRewardsAccessor {
    @Accessor("experience")
    int callresponse$experience();

    @Accessor("loot")
    ResourceLocation[] callresponse$loot();

    @Accessor("recipes")
    ResourceLocation[] callresponse$recipes();

    @Accessor("function")
    CommandFunction.CacheableFunction callresponse$function();
}
