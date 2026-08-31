package com.github.JumDa5he.callresponse.compat.api.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.dispatch.DispatchEventDefinition;
import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventBuilder.EnchantEntry;
import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventBuilder.ItemEntry;
import com.github.JumDa5he.callresponse.compat.api.datagen.DispatchEventBuilder.RewardEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.data.JsonCodecProvider;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 可扩展的派遣事件数据生成器：子类提供自己的 {@link DispatchEventBuilder} 列表，
 * 自动生成 data/&lt;modid&gt;/dispatch_events/*.json 事件定义，并在同一次生成中
 * 自动产出对应的 data/&lt;modid&gt;/loot_table/dispatch/*.json 奖励战利品表
 * （与原版配方自动生成解锁进度类似）。
 */
public abstract class DispatchEventProvider extends JsonCodecProvider<DispatchEventDefinition> {
    private final PackOutput.PathProvider lootPathProvider;
    private final List<DispatchEventBuilder> builders = new ArrayList<>();

    protected DispatchEventProvider(PackOutput output, String modid,
                                    CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, PackOutput.Target.DATA_PACK, "dispatch_events",
                DispatchEventDefinition.CODEC, lookupProvider, modid);
        this.lootPathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "loot_table");
    }

    @Override
    protected final void gather() {
        gatherDispatchEvent(new Saver(this));
    }

    public abstract void gatherDispatchEvent(Saver saver);

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        CompletableFuture<?> eventsFuture = super.run(cache);
        return eventsFuture.thenCompose(ignored -> lookupProvider.thenCompose(registries -> {
            List<CompletableFuture<?>> futures = new ArrayList<>();
            for (DispatchEventBuilder builder : builders) {
                futures.add(saveLootTable(cache, builder, registries));
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        }));
    }

    private CompletableFuture<?> saveLootTable(CachedOutput cache, DispatchEventBuilder builder,
                                               HolderLookup.Provider registries) {
        LootTable table = LootTable.lootTable()
                .setParamSet(LootContextParamSets.ADVANCEMENT_REWARD)
                .setRandomSequence(builder.lootKey().identifier())
                .withPool(pool(builder.rewards(), builder.rollsMin(), builder.rollsMax(), registries))
                .build();
        Path path = lootPathProvider.json(builder.lootKey().identifier());
        return DataProvider.saveStable(cache, registries, LootTable.DIRECT_CODEC, table, path);
    }

    private LootPool.Builder pool(List<RewardEntry> rewards, int rollsMin, int rollsMax,
                                  HolderLookup.Provider registries) {
        LootPool.Builder pool = LootPool.lootPool()
                .setRolls(UniformGenerator.between(rollsMin, rollsMax));
        rewards.forEach(reward -> {
            var entry = entry(reward, registries);
            if (entry != null) {
                pool.add(entry);
            }
        });
        return pool;
    }

    private LootPoolEntryContainer.Builder<?> entry(RewardEntry reward, HolderLookup.Provider registries) {
        if (reward instanceof ItemEntry item) {
            Item resolved = BuiltInRegistries.ITEM.getValue(item.item());
            if (resolved == Items.AIR && !item.item().equals(BuiltInRegistries.ITEM.getKey(Items.AIR))) {
                CallResponseMod.LOGGER.warn("奖励条目引用的物品 {} 不存在，已跳过", item.item());
                return null;
            }
            // AIR 在旧数据里表示“本次抽取为空”。26.1 禁止把 air 当作物品条目，
            // 必须使用专门的空战利品条目才能保留原有概率语义。
            if (resolved == Items.AIR) {
                return EmptyLootItem.emptyItem().setWeight(item.weight());
            }
            return LootItem.lootTableItem(resolved)
                    .setWeight(item.weight())
                    .apply(SetItemCountFunction.setCount(
                            UniformGenerator.between(item.countMin(), item.countMax())));
        }
        EnchantEntry enchant = (EnchantEntry) reward;
        HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        var holder = lookup.getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, enchant.enchant()));
        return LootItem.lootTableItem(Items.BOOK)
                .setWeight(enchant.weight())
                .apply(new SetEnchantmentsFunction.Builder()
                        .withEnchantment(holder, UniformGenerator.between(enchant.levelMin(), enchant.levelMax())));
    }

    public static class Saver {
        private final DispatchEventProvider provider;

        private Saver(DispatchEventProvider provider) {
            this.provider = provider;
        }

        public void save(DispatchEventBuilder builder) {
            save(builder, builder.id());
        }

        public void save(DispatchEventBuilder builder, Identifier id) {
            provider.unconditional(id, builder.build());
            provider.builders.add(builder);
        }
    }
}
