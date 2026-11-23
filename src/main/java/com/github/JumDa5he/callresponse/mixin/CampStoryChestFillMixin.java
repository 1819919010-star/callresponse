package com.github.JumDa5he.callresponse.mixin;

import com.github.JumDa5he.callresponse.compat.story.CampStoryBooks;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 1.21 owns the native unpack call in this default interface method. */
@Mixin(RandomizableContainer.class)
public interface CampStoryChestFillMixin {
    @Redirect(method = "unpackLootTable", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;fill(Lnet/minecraft/world/Container;Lnet/minecraft/world/level/storage/loot/LootParams;J)V"))
    private void callresponse$campStoryFill(LootTable table, Container container, LootParams params, long seed) {
        CampStoryBooks.fill(table, container, params, seed);
    }
}
