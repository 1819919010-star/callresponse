package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockStateProvider extends BlockStateProvider {
    public ModBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, CallResponseMod.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        getVariantBuilder(ModBlocks.MAID_CROP_BLOCK.get())
                .partialState()
                .modelForState()
                .modelFile(models().getBuilder("block/maid_crop")
                        .parent(new ModelFile.UncheckedModelFile(mcLoc("builtin/entity")))
                        .texture("particle", mcLoc("block/moss_block")))
                .addModel();
    }
}
