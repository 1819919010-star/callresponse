package com.github.JumDa5he.callresponse.compat.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class MaidCropBlockEntity extends BlockEntity {
    public static final String DEFAULT_MODEL_ID = "touhou_little_maid:hakurei_reimu";
    private static final String MODEL_ID_TAG = "modelID";

    private String modelId;

    public MaidCropBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MAID_CROP_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        modelId = tag.contains(MODEL_ID_TAG, Tag.TAG_STRING) ? tag.getString(MODEL_ID_TAG) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (modelId != null) {
            tag.putString(MODEL_ID_TAG, modelId);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public @Nullable String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
        setChanged();
    }
}
