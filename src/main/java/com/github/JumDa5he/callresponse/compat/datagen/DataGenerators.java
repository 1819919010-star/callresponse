package com.github.JumDa5he.callresponse.compat.datagen;

import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;

public class DataGenerators {
    @SubscribeEvent
    public static void onGatherData(GatherDataEvent.Client event) {
        var generator = event.getGenerator();
        var lookupProvider = event.getLookupProvider();
        var output = generator.getPackOutput();

        event.createProvider(ModRecipeProvider.Runner::new);
        generator.addProvider(true, new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ModLootTableProvider::new, LootContextParamSets.BLOCK)),
                lookupProvider));
        generator.addProvider(true, new ModDispatchEventProvider(output, lookupProvider));

        generator.addProvider(true, new ModItemModelProvider(output));
        generator.addProvider(true, new ModBlockStateProvider(output));
    }
}
