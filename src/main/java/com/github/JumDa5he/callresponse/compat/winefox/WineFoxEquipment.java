package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.config.EmotionPassiveConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.Unbreakable;

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
        fox.setItemSlot(EquipmentSlot.HEAD, create(new ItemStack(Items.NETHERITE_HELMET), fox.getRandom(), count, minimum, maximum, fox.registryAccess()));
        fox.setItemSlot(EquipmentSlot.CHEST, create(new ItemStack(Items.NETHERITE_CHESTPLATE), fox.getRandom(), count, minimum, maximum, fox.registryAccess()));
        fox.setItemSlot(EquipmentSlot.MAINHAND, create(new ItemStack(Items.NETHERITE_SWORD), fox.getRandom(), count, minimum, maximum, fox.registryAccess()));
        fox.setItemSlot(EquipmentSlot.LEGS, create(new ItemStack(Items.NETHERITE_LEGGINGS), fox.getRandom(), count, minimum, maximum, fox.registryAccess()));
        fox.setItemSlot(EquipmentSlot.FEET, create(new ItemStack(Items.NETHERITE_BOOTS), fox.getRandom(), count, minimum, maximum, fox.registryAccess()));
    }

    static ItemStack create(ItemStack stack, RandomSource random, int count, int minimum, int maximum, RegistryAccess registries) {
        stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        if (count == 0) return stack;
        int desired = 1 + random.nextInt(count);
        // Registry snapshot includes modded, treasure and curse enchantments. Finite shuffle, no retries.
        List<Holder<Enchantment>> candidates = new ArrayList<>();
        registries.registryOrThrow(Registries.ENCHANTMENT).holders().forEach(candidates::add);
        for (int i = candidates.size() - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            Holder<Enchantment> entry = candidates.get(i); candidates.set(i, candidates.get(other)); candidates.set(other, entry);
        }
        List<Holder<Enchantment>> selected = new ArrayList<>();
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(stack));
        int chosen = 0;
        for (Holder<Enchantment> candidate : candidates) {
            if (chosen >= desired) break;
            ResourceLocation id = candidate.unwrapKey().map(key -> key.location()).orElse(null);
            try {
                // NeoForge's general applicability hook includes anvil-only and modded enchantments.
                if (id == null || !stack.supportsEnchantment(candidate)) continue;
                boolean compatible = true;
                for (Holder<Enchantment> prior : selected) {
                    // The native exclusion sets are checked in both directions.
                    if (!Enchantment.areCompatible(candidate, prior)) { compatible = false; break; }
                }
                if (!compatible) continue;
                chosen++;
                int level = minimum + random.nextInt(maximum - minimum + 1);
                if (level == 0) continue;
                // Native 1.21 components preserve the full configured 1..255 range.
                enchantments.set(candidate, level);
                EnchantmentHelper.setEnchantments(stack, enchantments.toImmutable());
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
