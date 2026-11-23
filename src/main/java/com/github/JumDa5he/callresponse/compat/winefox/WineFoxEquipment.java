package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.config.EmotionPassiveConfig;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Spawn-time equipment only: never used to repair, reroll or migrate an existing fox. */
public final class WineFoxEquipment {
    private static final long WARNING_INTERVAL_NANOS = 60_000_000_000L;
    private static long nextRangeWarning, nextEnchantmentWarning;

    private WineFoxEquipment() {}

    public static void equip(WineFoxEntity fox) {
        int count = EmotionPassiveConfig.WINE_FOX_ENCHANTMENT_COUNT.get();
        int configuredMin = EmotionPassiveConfig.WINE_FOX_MIN_ENCHANTMENT_LEVEL.get();
        int configuredMax = EmotionPassiveConfig.WINE_FOX_MAX_ENCHANTMENT_LEVEL.get();
        if (configuredMin > configuredMax) warnRange(configuredMin, configuredMax);
        int minimum = Math.min(configuredMin, configuredMax), maximum = Math.max(configuredMin, configuredMax);
        fox.setItemSlot(EquipmentSlot.HEAD, create(new ItemStack(Items.NETHERITE_HELMET), fox.getRandom(), count, minimum, maximum));
        fox.setItemSlot(EquipmentSlot.CHEST, create(new ItemStack(Items.NETHERITE_CHESTPLATE), fox.getRandom(), count, minimum, maximum));
        fox.setItemSlot(EquipmentSlot.MAINHAND, create(new ItemStack(Items.NETHERITE_SWORD), fox.getRandom(), count, minimum, maximum));
        fox.setItemSlot(EquipmentSlot.LEGS, create(new ItemStack(Items.NETHERITE_LEGGINGS), fox.getRandom(), count, minimum, maximum));
        fox.setItemSlot(EquipmentSlot.FEET, create(new ItemStack(Items.NETHERITE_BOOTS), fox.getRandom(), count, minimum, maximum));
    }

    static ItemStack create(ItemStack stack, RandomSource random, int count, int minimum, int maximum) {
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        if (count == 0) return stack;
        int desired = 1 + random.nextInt(count);
        // Registry snapshot includes modded, treasure and curse enchantments. Finite shuffle, no retries.
        List<Enchantment> candidates = new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValues());
        for (int i = candidates.size() - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            Enchantment entry = candidates.get(i); candidates.set(i, candidates.get(other)); candidates.set(other, entry);
        }
        List<Enchantment> selected = new ArrayList<>();
        ListTag enchantments = new ListTag();
        int chosen = 0;
        for (Enchantment candidate : candidates) {
            if (chosen >= desired) break;
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(candidate);
            try {
                // Forge's general applicability hook delegates to stack/item enchanting hooks by default.
                // This also respects an enchantment's broader canEnchant override (e.g. anvil-only use).
                if (id == null || !candidate.canEnchant(stack)) continue;
                boolean compatible = true;
                for (Enchantment prior : selected) {
                    // isCompatibleWith performs BOTH directions of checkCompatibility.
                    if (candidate == prior || !candidate.isCompatibleWith(prior)) { compatible = false; break; }
                }
                if (!compatible) continue;
                chosen++;
                int level = minimum + random.nextInt(maximum - minimum + 1);
                if (level == 0) continue;
                // ItemStack.enchant casts to byte in 1.20.1. Write native short NBT without that truncation.
                enchantments.add(EnchantmentHelper.storeEnchantment(id, level));
                stack.getOrCreateTag().put("Enchantments", enchantments);
                selected.add(candidate);
            } catch (RuntimeException | LinkageError failure) {
                warnEnchantment(id, failure);
            }
        }
        return stack;
    }

    private static synchronized void warnRange(int minimum, int maximum) {
        long now = System.nanoTime();
        if (nextRangeWarning != 0 && now - nextRangeWarning < 0) return;
        nextRangeWarning = now + WARNING_INTERVAL_NANOS;
        CallResponseMod.LOGGER.warn("Wine Fox enchantment range is reversed ({}..{}); using {}..{} for new equipment without changing TOML",
                minimum, maximum, maximum, minimum);
    }

    private static synchronized void warnEnchantment(ResourceLocation id, Throwable failure) {
        long now = System.nanoTime();
        if (nextEnchantmentWarning != 0 && now - nextEnchantmentWarning < 0) return;
        nextEnchantmentWarning = now + WARNING_INTERVAL_NANOS;
        CallResponseMod.LOGGER.warn("Skipping failed Wine Fox equipment enchantment {} (further enchantment warnings limited to once per minute)", id, failure);
    }
}
