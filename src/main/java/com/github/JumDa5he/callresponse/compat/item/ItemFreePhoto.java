package com.github.JumDa5he.callresponse.compat.item;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.tartaricacid.touhoulittlemaid.item.ItemPhoto;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;

/**
 * 种子女仆成熟后使用的临时照片。有效照片落地时会释放女仆，
 * 从而兼容女仆农场模式的自动收获流程。
 */
public final class ItemFreePhoto extends ItemPhoto {
    @Override
    public String getDescriptionId() {
        return "item.touhou_little_maid.photo";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!hasValidMaidData(stack)) {
            Player player = context.getPlayer();
            if (player != null && context.getLevel().isClientSide) {
                player.sendSystemMessage(Component.translatable(
                        "message.touhou_little_maid.photo.have_no_nbt_data"));
            }
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return hasValidMaidData(stack);
    }

    @Override
    public @Nullable Entity createEntity(Level level, Entity location, ItemStack stack) {
        if (!hasValidMaidData(stack)) {
            return null;
        }

        CompoundTag maidData = getMaidData(stack).copy();
        EntityMaid maid = new EntityMaid(level);
        MinecraftForge.EVENT_BUS.post(new MaidAndItemTransformEvent.ToMaid(maid, stack, maidData));
        maid.load(maidData);
        maid.setPos(location.position());
        return maid;
    }

    private static boolean hasValidMaidData(ItemStack stack) {
        if (!hasMaidData(stack)) {
            return false;
        }
        CompoundTag maidData = getMaidData(stack);
        if (!maidData.contains("id", Tag.TAG_STRING)
                || !maidData.contains(EntityMaid.MODEL_ID_TAG, Tag.TAG_STRING)
                || maidData.getString(EntityMaid.MODEL_ID_TAG).isBlank()) {
            return false;
        }
        return net.minecraft.world.entity.EntityType.by(maidData)
                .filter(type -> type == InitEntities.MAID.get())
                .isPresent();
    }
}
