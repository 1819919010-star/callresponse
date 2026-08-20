package com.github.JumDa5he.callresponse.compat.dispatch;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DispatchEventDefinition(String id, Category category, String title, String description,
                                      int durationMin, int durationMax, Emotion emotion, int weight,
                                      int cooldownMin, List<Reward> rewards) {
    public enum Category {
        WORK, PLAY;

        public static Category parse(String value) {
            return "play".equalsIgnoreCase(value) ? PLAY : WORK;
        }

        public String serializedName() {
            return name().toLowerCase();
        }
    }

    public record Emotion(int trust, int fear, int favor, int hunger) {
    }

    public record Reward(String type, String itemId, String enchantId, int countMin, int countMax,
                         int levelMin, int levelMax, int weight) {
        public ItemStack create(RandomSource random) {
            if ("enchant_book".equals(type)) {
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(enchantId));
                if (enchantment == null) return ItemStack.EMPTY;
                int level = between(random, levelMin, levelMax);
                return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, level));
            }
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item == null || item.getDefaultInstance().isEmpty()) return ItemStack.EMPTY;
            ItemStack stack = new ItemStack(item);
            stack.setCount(Math.min(stack.getMaxStackSize(), between(random, countMin, countMax)));
            return stack;
        }

        public ItemStack preview() {
            return create(RandomSource.create(31L * hashCode()));
        }

        private static int between(RandomSource random, int min, int max) {
            int low = Math.max(1, Math.min(min, max));
            int high = Math.max(low, Math.max(min, max));
            return low + random.nextInt(high - low + 1);
        }
    }

    public long rollDurationMillis(RandomSource random) {
        int low = Math.max(10, Math.min(durationMin, durationMax));
        int high = Math.min(60, Math.max(durationMin, durationMax));
        return (long) (low + random.nextInt(Math.max(1, high - low + 1))) * 60_000L;
    }

    public List<ItemStack> rollRewards(RandomSource random) {
        if (rewards.isEmpty()) return List.of();
        List<Reward> available = new ArrayList<>(rewards);
        List<ItemStack> result = new ArrayList<>();
        int count = Math.min(available.size(), 1 + random.nextInt(3));
        for (int i = 0; i < count && !available.isEmpty(); i++) {
            int total = available.stream().mapToInt(r -> Math.max(1, r.weight())).sum();
            int roll = random.nextInt(total);
            Reward selected = available.get(0);
            for (Reward reward : available) {
                roll -= Math.max(1, reward.weight());
                if (roll < 0) {
                    selected = reward;
                    break;
                }
            }
            available.remove(selected);
            ItemStack stack = selected.create(random);
            if (!stack.isEmpty()) result.add(stack);
        }
        return Collections.unmodifiableList(result);
    }
}
