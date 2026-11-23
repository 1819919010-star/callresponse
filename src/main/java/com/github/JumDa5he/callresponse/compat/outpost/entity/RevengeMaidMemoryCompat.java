package com.github.JumDa5he.callresponse.compat.outpost.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional registered slots for the revenge Brain only; no memory values or addon AI. */
final class RevengeMaidMemoryCompat {
    // Extend only this explicit namespace allowlist, not a list of individual memory IDs.
    private static final Set<String> MOD_IDS = Set.of("maid_useful_task");

    static List<MemoryModuleType<?>> extend(Collection<? extends MemoryModuleType<?>> required) {
        // Read Forge's actual registry on every provider creation, after normal registration.
        // Never cache an early snapshot or construct/register an optional addon's types.
        return extend(required, ForgeRegistries.MEMORY_MODULE_TYPES.getEntries());
    }

    static List<MemoryModuleType<?>> extend(Collection<? extends MemoryModuleType<?>> required,
            Iterable<? extends Map.Entry<ResourceKey<MemoryModuleType<?>>, MemoryModuleType<?>>> entries) {
        var memories = new LinkedHashSet<MemoryModuleType<?>>(required);
        for (var entry : entries) {
            if (MOD_IDS.contains(entry.getKey().location().getNamespace())) memories.add(entry.getValue());
        }
        // Native Brain initializes these slots to Optional.empty; do not invent work-state values.
        return List.copyOf(memories);
    }

    private RevengeMaidMemoryCompat() {}
}
