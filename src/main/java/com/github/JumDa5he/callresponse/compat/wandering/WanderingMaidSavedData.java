package com.github.JumDa5he.callresponse.compat.wandering;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WanderingMaidSavedData extends SavedData {
    public static final String DATA_NAME = "callresponse_wandering_maid";

    private static final Identifier IDENTIFIER = Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "wandering_maid");

    public static final Codec<WanderingMaidSavedData> CODEC = RecordCodecBuilder.create(ins -> ins.group(
            Codec.LONG.fieldOf("NextAttemptTick").forGetter(d -> d.nextAttemptTick),
            Codec.INT.fieldOf("SpawnChance").forGetter(d -> d.spawnChance),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING.listOf())
                    .fieldOf("SkinPools").forGetter(d -> d.skinPools)
    ).apply(ins, WanderingMaidSavedData::new));

    private long nextAttemptTick;
    private int spawnChance = 25;
    private final Map<UUID, List<String>> skinPools = new HashMap<>();

    private WanderingMaidSavedData() {
    }

    private WanderingMaidSavedData(long nextAttemptTick, int spawnChance, Map<UUID, List<String>> skinPools) {
        this.nextAttemptTick = nextAttemptTick;
        this.spawnChance = spawnChance;
        this.skinPools.putAll(skinPools);
    }

    public static SavedDataType<WanderingMaidSavedData> factory() {
        return new SavedDataType<>(IDENTIFIER, WanderingMaidSavedData::new, CODEC, DataFixTypes.ENTITY_CHUNK);
    }

    public static WanderingMaidSavedData get(ServerLevel overworld) {
        SavedDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(WanderingMaidSavedData.factory());
    }

    public long nextAttemptTick() {
        return nextAttemptTick;
    }

    public void setNextAttemptTick(long value) {
        nextAttemptTick = value;
        setDirty();
    }

    public int spawnChance() {
        return spawnChance;
    }

    public void recordSpawnResult(boolean success) {
        spawnChance = success ? 25 : Math.min(75, spawnChance + 25);
        setDirty();
    }

    public List<String> skinPool(UUID player) {
        return List.copyOf(skinPools.getOrDefault(player, List.of()));
    }

    public void setSkinPool(UUID player, Collection<String> models) {
        Set<String> distinct = new LinkedHashSet<>(models);
        if (distinct.isEmpty()) {
            skinPools.remove(player);
        } else {
            skinPools.put(player, List.copyOf(distinct));
        }
        setDirty();
    }
}