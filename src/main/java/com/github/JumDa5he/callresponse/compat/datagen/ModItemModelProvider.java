package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, String modid, ExistingFileHelper existingFileHelper) {
        super(output, modid, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        singleTexture("emotion_book", mcLoc("item/generated"), "layer0", mcLoc("item/book"));
        singleTexture("hunt_order", mcLoc("item/generated"), "layer0", modLoc("item/hunt_order"));
        singleTexture("moreeat_bauble", mcLoc("item/generated"), "layer0", modLoc("item/feast"));
        singleTexture("noeat_bauble", mcLoc("item/generated"), "layer0", modLoc("item/no_eat"));
        singleTexture("wandering_maid_book", mcLoc("item/generated"), "layer0", mcLoc("item/writable_book"));
        singleTexture("maid_seed", mcLoc("item/generated"), "layer0", mcLoc("item/wheat_seeds"));
        singleTexture("disposable_favorability_tool_add", mcLoc("item/generated"), "layer0",
                ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "item/favorability_tool_add"));
        singleTexture("free_photo", mcLoc("item/generated"), "layer0",
                ResourceLocation.fromNamespaceAndPath("touhou_little_maid", "item/photo"));
    }
}
