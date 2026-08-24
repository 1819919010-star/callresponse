package com.github.JumDa5he.callresponse.compat.api.datagen;

import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventBuilder.EnchantEntry;
import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventBuilder.ItemEntry;
import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventBuilder.RewardEntry;
import com.github.JumDa5he.callresponse.compat.dispatch.DispatchEventDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 可扩展的派遣事件数据生成器：子类提供自己的 {@link DispatchEventBuilder} 列表，
 * 自动生成 data/&lt;modid&gt;/dispatch_events/*.json 事件定义，并在同一次生成中
 * 自动产出对应的 data/&lt;modid&gt;/loot_tables/dispatch/*.json 奖励战利品表
 * （1.20.1 没有 LootTable 的 Codec，战利品表按 1.20.1 格式手写）。
 */
public abstract class DispatchEventProvider implements DataProvider {
    protected final CompletableFuture<HolderLookup.Provider> lookupProvider;
    private final PackOutput.PathProvider eventPathProvider;
    private final PackOutput.PathProvider lootPathProvider;
    private final List<DispatchEventBuilder> builders = new ArrayList<>();

    protected DispatchEventProvider(PackOutput output, String modid,
                                    CompletableFuture<HolderLookup.Provider> lookupProvider,
                                    ExistingFileHelper existingFileHelper) {
        this.lookupProvider = lookupProvider;
        this.eventPathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "dispatch_events");
        // Forge 1.20.1 的战利品表资源目录固定为 loot_tables（复数）。
        // loot_table 是高版本数据生成时使用的注册表路径，直接带入会导致 1.20 找不到奖励表。
        this.lootPathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "loot_tables");
        gatherDispatchEvent(new Saver(this));
    }

    public abstract void gatherDispatchEvent(Saver saver);

    @Override
    public String getName() {
        return "Dispatch Events";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return lookupProvider.thenCompose(registries -> {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (DispatchEventBuilder builder : builders) {
                futures.add(saveEvent(cache, builder));
                futures.add(saveLootTable(cache, builder));
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        });
    }

    private CompletableFuture<?> saveEvent(CachedOutput cache, DispatchEventBuilder builder) {
        Path path = eventPathProvider.json(builder.id());
        return DispatchEventDefinition.CODEC.encodeStart(JsonOps.INSTANCE, builder.build())
                .resultOrPartial(error -> {
                    throw new IllegalStateException("编码派遣事件 " + builder.id() + " 失败: " + error);
                })
                .map(element -> DataProvider.saveStable(cache, element, path))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    private CompletableFuture<?> saveLootTable(CachedOutput cache, DispatchEventBuilder builder) {
        Path path = lootPathProvider.json(builder.lootKey());
        return DataProvider.saveStable(cache, lootTableJson(builder), path);
    }

    private static JsonObject lootTableJson(DispatchEventBuilder builder) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:advancement_reward");
        root.addProperty("random_sequence", builder.lootKey().toString());
        JsonArray pools = new JsonArray();
        JsonObject pool = new JsonObject();
        pool.add("rolls", uniformNumber(builder.rollsMin(), builder.rollsMax()));
        JsonArray entries = new JsonArray();
        for (RewardEntry reward : builder.rewards()) {
            JsonObject entry = entryJson(reward);
            if (entry != null) {
                entries.add(entry);
            }
        }
        pool.add("entries", entries);
        pools.add(pool);
        root.add("pools", pools);
        return root;
    }

    private static JsonObject entryJson(RewardEntry reward) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        JsonArray functions = new JsonArray();
        if (reward instanceof ItemEntry item) {
            entry.addProperty("name", item.item().toString());
            entry.addProperty("weight", item.weight());
            JsonObject count = uniformNumber(item.countMin(), item.countMax());
            JsonObject fn = new JsonObject();
            fn.addProperty("function", "minecraft:set_count");
            fn.add("count", count);
            functions.add(fn);
        } else if (reward instanceof EnchantEntry enchant) {
            entry.addProperty("name", "minecraft:book");
            entry.addProperty("weight", enchant.weight());
            JsonObject level = uniformNumber(enchant.levelMin(), enchant.levelMax());
            JsonObject enchantments = new JsonObject();
            enchantments.add(enchant.enchant().toString(), level);
            JsonObject fn = new JsonObject();
            fn.addProperty("function", "minecraft:set_enchantments");
            fn.add("enchantments", enchantments);
            functions.add(fn);
        }
        entry.add("functions", functions);
        return entry;
    }

    /** 1.20.1 的 NumberProvider JSON 是平铺 min/max 结构（无 value 嵌套）。 */
    private static JsonObject uniformNumber(float min, float max) {
        JsonObject uniform = new JsonObject();
        uniform.addProperty("type", "minecraft:uniform");
        uniform.addProperty("min", min);
        uniform.addProperty("max", max);
        return uniform;
    }

    public static class Saver {
        private final DispatchEventProvider provider;

        private Saver(DispatchEventProvider provider) {
            this.provider = provider;
        }

        public void save(DispatchEventBuilder builder) {
            save(builder, builder.id());
        }

        public void save(DispatchEventBuilder builder, ResourceLocation id) {
            provider.builders.add(builder);
        }
    }
}
