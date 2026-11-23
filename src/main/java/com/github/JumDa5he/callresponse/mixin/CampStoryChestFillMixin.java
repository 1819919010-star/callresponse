package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.story.CampStoryBooks;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RandomizableContainerBlockEntity.class)
public abstract class CampStoryChestFillMixin {
    @Redirect(method = "unpackLootTable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;fill(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/storage/loot/LootParams;J)V"))
    private void callresponse$fillStoryChest(LootTable table, Container container, LootParams params, long seed) {
        CampStoryBooks.fill(table, container, params, seed);
    }
}
