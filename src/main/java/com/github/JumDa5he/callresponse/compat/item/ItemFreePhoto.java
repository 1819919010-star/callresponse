package com.github.JumDa5he.callresponse.compat.item;

import com.github.JumDa5he.callresponse.compat.emotion.MaidReleaseHealth;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.item.ItemPhoto;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/** Photo produced by the maid crop. Dropping a valid photo releases its maid for automated harvesting. */
public class ItemFreePhoto extends ItemPhoto {
    @Override
    public String getDescriptionId() {
        return "item.touhou_little_maid.photo";
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (validData(context.getItemInHand()) == null) {
            Player player = context.getPlayer();
            if (player != null && context.getLevel().isClientSide) {
                player.sendSystemMessage(Component.translatable("message.touhou_little_maid.photo.have_no_nbt_data"));
            }
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult spawnFromStore(UseOnContext context, Player player, Level level, EntityMaid maid, Runnable consume) {
        ItemStack stack = context.getItemInHand();
        CompoundTag data = validData(stack);
        if (data == null) return InteractionResult.FAIL;
        loadPhoto(maid, stack, data);
        maid.setPos(context.getClickLocation());
        if (level instanceof ServerLevel && !MaidReleaseHealth.addReleased(level, maid)) return InteractionResult.FAIL;
        maid.spawnExplosionParticle();
        consume.run();
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return validData(stack) != null;
    }

    @Override
    public @Nullable Entity createEntity(Level level, Entity location, ItemStack stack) {
        CompoundTag data = validData(stack);
        if (data == null) return null;
        EntityMaid maid = new EntityMaid(level);
        loadPhoto(maid, stack, data);
        maid.setPos(location.position());
        return maid;
    }

    private static @Nullable CompoundTag validData(ItemStack stack) {
        CustomData component = stack.get(InitDataComponent.MAID_INFO);
        if (component == null) return null;
        CompoundTag data = component.copyTag();
        if (!data.contains(EntityMaid.MODEL_ID_TAG, Tag.TAG_STRING)
                || data.getString(EntityMaid.MODEL_ID_TAG).isBlank()) return null;
        // Existing 1.21 crop photos and native saveWithoutId payloads carry the type implicitly.
        if (data.contains("id") && EntityType.byString(data.getString("id")).orElse(null) != EntityMaid.TYPE) return null;
        return data;
    }

    private static void loadPhoto(EntityMaid maid, ItemStack stack, CompoundTag data) {
        NeoForge.EVENT_BUS.post(new MaidAndItemTransformEvent.ToMaid(maid, stack, data));
        maid.load(data);
    }
}
