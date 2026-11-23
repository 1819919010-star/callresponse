package com.github.JumDa5he.callresponse.compat.outpost.entity;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Optional registered slots for the revenge Brain only; no memory values or addon AI. */
final class RevengeMaidMemoryCompat {
    // Add an explicitly supported mod namespace here; individual memory IDs need no mapping.
    private static final Set<String> MOD_IDS = Set.of("maid_useful_task");

    static List<MemoryModuleType<?>> extend(Collection<? extends MemoryModuleType<?>> required) {
        // Resolve on each provider creation, never cache a possibly incomplete early registry snapshot.
        return extend(required, BuiltInRegistries.MEMORY_MODULE_TYPE);
    }

    static List<MemoryModuleType<?>> extend(Collection<? extends MemoryModuleType<?>> required,
                                            Registry<MemoryModuleType<?>> registry) {
        var memories = new LinkedHashSet<MemoryModuleType<?>>(required);
        for (var entry : registry.entrySet()) {
            if (MOD_IDS.contains(entry.getKey().location().getNamespace())) memories.add(entry.getValue());
        }
        // Brain initializes registered slots as Optional.empty; never invent a work-state value.
        return List.copyOf(memories);
    }

    private RevengeMaidMemoryCompat() {}
}
