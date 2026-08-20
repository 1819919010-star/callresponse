package com.github.JumDa5he.callresponse.compat.datagen;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.block.ModBlocks;
import com.github.JumDa5he.callresponse.compat.block.RewardBoxBlock;
import net.minecraft.core.Direction;
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

        var rewardModel = models().getBuilder("block/reward_box")
                .parent(new ModelFile.UncheckedModelFile(mcLoc("block/block")))
                .texture("particle", modLoc("block/reward_box_front_16x"))
                .texture("front", modLoc("block/reward_box_front_16x"))
                .texture("back", modLoc("block/reward_box_back_16x"))
                .texture("left", modLoc("block/reward_box_left_16x"))
                .texture("right", modLoc("block/reward_box_right_16x"))
                .texture("top", modLoc("block/reward_box_top_16x"))
                .texture("bottom", modLoc("block/reward_box_bottom_16x"))
                .element().from(0, 0, 0).to(16, 16, 16)
                        .face(Direction.DOWN).texture("#bottom").cullface(Direction.DOWN).end()
                        .face(Direction.UP).texture("#top").cullface(Direction.UP).end()
                        .face(Direction.NORTH).texture("#front").cullface(Direction.NORTH).end()
                        .face(Direction.SOUTH).texture("#back").cullface(Direction.SOUTH).end()
                        .face(Direction.WEST).texture("#left").cullface(Direction.WEST).end()
                        .face(Direction.EAST).texture("#right").cullface(Direction.EAST).end()
                .end();
        getVariantBuilder(ModBlocks.REWARD_BOX.get())
                .partialState().with(RewardBoxBlock.FACING, Direction.NORTH)
                .modelForState().modelFile(rewardModel).rotationY(0).addModel()
                .partialState().with(RewardBoxBlock.FACING, Direction.EAST)
                .modelForState().modelFile(rewardModel).rotationY(90).addModel()
                .partialState().with(RewardBoxBlock.FACING, Direction.SOUTH)
                .modelForState().modelFile(rewardModel).rotationY(180).addModel()
                .partialState().with(RewardBoxBlock.FACING, Direction.WEST)
                .modelForState().modelFile(rewardModel).rotationY(270).addModel();
    }
}
