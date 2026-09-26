package com.github.JumDa5he.callresponse.mixin.accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomizableContainerBlockEntity.class)
public interface OutpostLootContainerAccessor {
    @Accessor("lootTable")
    @Nullable ResourceKey<LootTable> callresponse$getLootTable();

    @Accessor("lootTableSeed")
    long callresponse$getLootTableSeed();

}
