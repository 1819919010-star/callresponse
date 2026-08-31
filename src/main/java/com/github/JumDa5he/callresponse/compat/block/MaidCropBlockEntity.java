package com.github.JumDa5he.callresponse.compat.block;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public class MaidCropBlockEntity extends BlockEntity {
    public static final String DEFAULT_MODEL_ID = "touhou_little_maid:hakurei_reimu";
    private String modelID = DEFAULT_MODEL_ID;

    public MaidCropBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlocks.MAID_CROP_BLOCK_ENTITY.get(), pos, blockState);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("modelID", Codec.STRING, modelID);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        modelID = input.read("modelID", Codec.STRING).orElse(DEFAULT_MODEL_ID);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Nullable
    public String getModelID() {
        return modelID;
    }

    public void setModelID(String modelID) {
        this.modelID = modelID;
        setChanged();
    }
}