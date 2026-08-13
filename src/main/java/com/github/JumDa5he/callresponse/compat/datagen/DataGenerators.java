package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DataGenerators {
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        generator.getVanillaPack(event.includeServer()).addProvider(output ->
                new ModRecipeProvider(output, lookupProvider));
        generator.getVanillaPack(event.includeServer()).addProvider(output -> new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ModLootTableProvider::new, LootContextParamSets.BLOCK)),
                lookupProvider));

        generator.getVanillaPack(event.includeClient()).addProvider(output ->
                new ModItemModelProvider(output, CallResponseMod.MOD_ID, existingFileHelper));
        generator.getVanillaPack(event.includeClient()).addProvider(output ->
                new ModBlockStateProvider(output, existingFileHelper));
    }
}
