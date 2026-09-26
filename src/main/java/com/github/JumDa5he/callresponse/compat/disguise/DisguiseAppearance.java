package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** Only the fields needed to draw a maid model; never copies entity identity or attributes. */
public record DisguiseAppearance(String modelId, boolean ysmModel, String ysmModelId,
                                 String ysmTexture, Component ysmName) {
    public static DisguiseAppearance capture(EntityMaid maid) {
        return new DisguiseAppearance(maid.getModelId(), maid.isYsmModel(), maid.getYsmModelId(),
                maid.getYsmModelTexture(), maid.getYsmModelName());
    }

    public boolean usable() {
        return ysmModel ? ysmModelId != null && !ysmModelId.isBlank()
                : modelId != null && !modelId.isBlank();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("ModelId", modelId == null ? "" : modelId);
        tag.putBoolean("YsmModel", ysmModel);
        tag.putString("YsmModelId", ysmModelId == null ? "" : ysmModelId);
        tag.putString("YsmTexture", ysmTexture == null ? "" : ysmTexture);
        tag.putString("YsmName", Component.Serializer.toJson(
                ysmName == null ? Component.empty() : ysmName, RegistryAccess.EMPTY));
        return tag;
    }

    public static DisguiseAppearance load(CompoundTag tag) {
        Component name;
        try {
            name = Component.Serializer.fromJson(tag.getString("YsmName"), RegistryAccess.EMPTY);
        } catch (RuntimeException ignored) {
            name = null;
        }
        return new DisguiseAppearance(tag.getString("ModelId"), tag.getBoolean("YsmModel"),
                tag.getString("YsmModelId"), tag.getString("YsmTexture"),
                name == null ? Component.empty() : name);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(modelId == null ? "" : modelId);
        buffer.writeBoolean(ysmModel);
        buffer.writeUtf(ysmModelId == null ? "" : ysmModelId);
        buffer.writeUtf(ysmTexture == null ? "" : ysmTexture);
        buffer.writeUtf(Component.Serializer.toJson(
                ysmName == null ? Component.empty() : ysmName, RegistryAccess.EMPTY));
    }

    public static DisguiseAppearance read(FriendlyByteBuf buffer) {
        return new DisguiseAppearance(buffer.readUtf(), buffer.readBoolean(), buffer.readUtf(),
                buffer.readUtf(), Component.Serializer.fromJson(buffer.readUtf(), RegistryAccess.EMPTY));
    }
}
