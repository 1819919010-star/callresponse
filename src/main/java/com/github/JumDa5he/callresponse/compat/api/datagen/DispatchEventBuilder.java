package com.github.JumDa5he.callresponse.compat.api.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.dispatch.DispatchEventDefinition;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CacheableFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 数据生成用的派遣事件构建器：既是事件定义的数据源，也携带该事件的奖励条目。 */
public final class DispatchEventBuilder {
    private final ResourceLocation id;
    private DispatchEventDefinition.Category category = DispatchEventDefinition.Category.WORK;
    private Component title = Component.empty();
    private Component description = Component.empty();
    private IntProvider duration = ConstantInt.of(10);
    private DispatchEventDefinition.Emotion emotion = new DispatchEventDefinition.Emotion(0, 0, 0, 0);
    private ResourceLocation command;
    private int weight = 1;
    private int cooldown = 0;
    private int experience = 0;
    private final List<ResourceKey<LootTable>> extraLoot = new ArrayList<>();
    private final List<RewardEntry> rewards = new ArrayList<>();

    private DispatchEventBuilder(ResourceLocation id) {
        this.id = id;
    }

    public static DispatchEventBuilder builder(String path) {
        return builder(ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, path));
    }

    public static DispatchEventBuilder builder(ResourceLocation id) {
        return new DispatchEventBuilder(id);
    }

    public ResourceLocation id() {
        return id;
    }

    public DispatchEventBuilder category(DispatchEventDefinition.Category category) {
        this.category = category;
        return this;
    }

    public DispatchEventBuilder title(String title) {
        return title(Component.literal(title));
    }

    public DispatchEventBuilder title(Component title) {
        this.title = title;
        return this;
    }

    public DispatchEventBuilder description(String description) {
        return description(Component.literal(description));
    }

    public DispatchEventBuilder description(Component description) {
        this.description = description;
        return this;
    }

    public DispatchEventBuilder duration(IntProvider duration) {
        this.duration = duration;
        return this;
    }

    public DispatchEventBuilder duration(int min, int max) {
        return duration(UniformInt.of(min, max));
    }

    public DispatchEventBuilder emotion(DispatchEventDefinition.Emotion emotion) {
        this.emotion = emotion;
        return this;
    }

    public DispatchEventBuilder emotion(int trust, int fear, int favor, int hunger) {
        return emotion(new DispatchEventDefinition.Emotion(trust, fear, favor, hunger));
    }

    public DispatchEventBuilder weight(int weight) {
        this.weight = weight;
        return this;
    }

    public DispatchEventBuilder cooldown(int cooldown) {
        this.cooldown = cooldown;
        return this;
    }

    public DispatchEventBuilder experience(int experience) {
        this.experience = experience;
        return this;
    }

    public DispatchEventBuilder command(ResourceLocation command) {
        this.command = command;
        return this;
    }

    public DispatchEventBuilder extraLoot(ResourceKey<LootTable> loot) {
        extraLoot.add(loot);
        return this;
    }

    public DispatchEventBuilder item(Item item, int countMin, int countMax, int weight) {
        return item(BuiltInRegistries.ITEM.getKey(item), countMin, countMax, weight);
    }

    public DispatchEventBuilder item(ResourceLocation item, int countMin, int countMax, int weight) {
        rewards.add(new ItemEntry(item, countMin, countMax, weight));
        return this;
    }

    /** 一次添加多个物品，共享数量范围与权重。 */
    public DispatchEventBuilder items(int countMin, int countMax, int weight, Item... items) {
        for (Item item : items) {
            item(item, countMin, countMax, weight);
        }
        return this;
    }

    /** 一次添加多个可能不存在的物品（按 id 引用），共享数量范围与权重。 */
    public DispatchEventBuilder items(int countMin, int countMax, int weight, ResourceLocation... items) {
        for (ResourceLocation item : items) {
            item(item, countMin, countMax, weight);
        }
        return this;
    }

    public DispatchEventBuilder enchant(ResourceLocation enchant, int levelMin, int levelMax, int weight) {
        rewards.add(new EnchantEntry(enchant, levelMin, levelMax, weight));
        return this;
    }

    public List<RewardEntry> rewards() {
        return List.copyOf(rewards);
    }

    public ResourceKey<LootTable> lootKey() {
        return ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "dispatch/" + id.getPath()));
    }

    public DispatchEventBuilder save(DispatchEventProvider.Saver saver){
        saver.save(this);
        return this;
    }

    public DispatchEventDefinition build() {
        List<ResourceKey<LootTable>> loot = new ArrayList<>(extraLoot.size() + 1);
        loot.add(lootKey());
        loot.addAll(extraLoot);
        return new DispatchEventDefinition(category, title, description, duration, emotion, weight, cooldown,
                new AdvancementRewards(experience, loot, List.of(), command == null ? Optional.empty() : Optional.of(new CacheableFunction(command))));
    }

    public sealed interface RewardEntry permits ItemEntry, EnchantEntry {
    }

    public record ItemEntry(ResourceLocation item, int countMin, int countMax, int weight) implements RewardEntry {
    }

    public record EnchantEntry(ResourceLocation enchant, int levelMin, int levelMax, int weight) implements RewardEntry {
    }
}
