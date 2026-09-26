package com.github.JumDa5he.callresponse.mixin.accessor;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RandomizableContainerBlockEntity.class)
public interface OutpostLootContainerAccessor {
    @Accessor("lootTable")
    @Nullable ResourceLocation callresponse$getLootTable();

    @Accessor("lootTableSeed")
    long callresponse$getLootTableSeed();

    @Invoker("unpackLootTable")
    void callresponse$unpackLootTable(@Nullable Player player);
}
