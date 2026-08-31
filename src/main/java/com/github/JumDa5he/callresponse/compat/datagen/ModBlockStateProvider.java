package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.google.gson.JsonParser;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.util.concurrent.CompletableFuture;

/** Generates block states and models without relying on the removed 1.21 model-generator API. */
public final class ModBlockStateProvider implements DataProvider {
    private final PackOutput.PathProvider blockStates;
    private final PackOutput.PathProvider blockModels;

    public ModBlockStateProvider(PackOutput output) {
        blockStates = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates");
        blockModels = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/block");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var maidCropModel = JsonParser.parseString("""
                {"parent":"minecraft:builtin/entity","textures":{"particle":"minecraft:block/moss_block"}}
                """);
        var maidCropState = JsonParser.parseString("""
                {"variants":{"":{"model":"callresponse:block/maid_crop"}}}
                """);
        var rewardModel = JsonParser.parseString("""
                {"parent":"minecraft:block/block","textures":{"particle":"callresponse:block/reward_box_front_16x","front":"callresponse:block/reward_box_front_16x","back":"callresponse:block/reward_box_back_16x","left":"callresponse:block/reward_box_left_16x","right":"callresponse:block/reward_box_right_16x","top":"callresponse:block/reward_box_top_16x","bottom":"callresponse:block/reward_box_bottom_16x"},"elements":[{"from":[0,0,0],"to":[16,16,16],"faces":{"down":{"texture":"#bottom","cullface":"down"},"up":{"texture":"#top","cullface":"up"},"north":{"texture":"#front","cullface":"north"},"south":{"texture":"#back","cullface":"south"},"west":{"texture":"#left","cullface":"west"},"east":{"texture":"#right","cullface":"east"}}}]}
                """);
        var rewardState = JsonParser.parseString("""
                {"variants":{"facing=north":{"model":"callresponse:block/reward_box"},"facing=east":{"model":"callresponse:block/reward_box","y":90},"facing=south":{"model":"callresponse:block/reward_box","y":180},"facing=west":{"model":"callresponse:block/reward_box","y":270}}}
                """);
        Identifier maidCrop = Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "maid_crop");
        Identifier rewardBox = Identifier.fromNamespaceAndPath(CallResponseMod.MOD_ID, "reward_box");
        return CompletableFuture.allOf(
                DataProvider.saveStable(cache, maidCropModel, blockModels.json(maidCrop)),
                DataProvider.saveStable(cache, maidCropState, blockStates.json(maidCrop)),
                DataProvider.saveStable(cache, rewardModel, blockModels.json(rewardBox)),
                DataProvider.saveStable(cache, rewardState, blockStates.json(rewardBox)));
    }

    @Override
    public String getName() {
        return "CallResponse block states and models";
    }
}
