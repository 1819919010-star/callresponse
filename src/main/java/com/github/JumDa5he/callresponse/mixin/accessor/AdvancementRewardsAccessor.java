package com.github.JumDa5he.callresponse.mixin.accessor;

import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CacheableFunction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Optional;

@Mixin(AdvancementRewards.class)
public interface AdvancementRewardsAccessor {
    @Accessor("experience")
    int callresponse$experience();

    @Accessor("loot")
    List<ResourceKey<LootTable>> callresponse$loot();

    @Accessor("recipes")
    List<Identifier> callresponse$recipes();

    @Accessor("function")
    Optional<CacheableFunction> callresponse$function();
}
