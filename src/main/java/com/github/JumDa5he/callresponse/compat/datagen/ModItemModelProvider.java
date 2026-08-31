package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.google.gson.JsonParser;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Generates the legacy model JSON consumed by the 26.1 item-definition files. */
public final class ModItemModelProvider implements DataProvider {
    private final PackOutput.PathProvider models;

    public ModItemModelProvider(PackOutput output) {
        models = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/item");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("emotion_book", generated("minecraft:item/book"));
        entries.put("hunt_order", generated("callresponse:item/hunt_order"));
        entries.put("moreeat_bauble", generated("callresponse:item/feast"));
        entries.put("noeat_bauble", generated("callresponse:item/no_eat"));
        entries.put("wandering_maid_book", generated("minecraft:item/writable_book"));
        entries.put("maid_seed", generated("callresponse:item/maid_seed"));
        entries.put("dispatch_book", generated("callresponse:item/dispatch_book"));
        entries.put("disposable_favorability_tool_add", generated("touhou_little_maid:item/favorability_tool_add"));
        entries.put("free_photo", generated("touhou_little_maid:item/photo"));
        entries.put("reward_box", "{\"parent\":\"callresponse:block/reward_box\"}");
        entries.put("facility_capacity_tool", generated("minecraft:item/amethyst_shard"));

        return CompletableFuture.allOf(entries.entrySet().stream()
                .map(entry -> DataProvider.saveStable(cache, JsonParser.parseString(entry.getValue()),
                        models.json(Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, entry.getKey()))))
                .toArray(CompletableFuture[]::new));
    }

    private static String generated(String texture) {
        return "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + texture + "\"}}";
    }

    @Override
    public String getName() {
        return "CallResponse item models";
    }
}
