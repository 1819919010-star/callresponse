package com.github.JumDa5he.callresponse.compat.item;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.github.tartaricacid.touhoulittlemaid.item.ItemPhoto;
import com.github.tartaricacid.touhoulittlemaid.util.MaidItemStorageHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * 与 ItemPhoto（车万女仆的照片）完全一致的物品，唯一区别：
 * 放置女仆时不校验照片上的主人 UUID，任何玩家都可以释放。
 */
public class ItemFreePhoto extends ItemPhoto {
    public ItemFreePhoto(Identifier id) {
        super(id);
    }

    /**
     * 复制 AbstractStoreMaidItem#spawnFromStore 的完整逻辑，仅去掉主人校验。
     */
    @Override
    public InteractionResult spawnFromStore(UseOnContext context, Player player, Level worldIn, EntityMaid maid, Runnable runnable) {
        ItemStack stack = context.getItemInHand();
        CustomData compoundData = stack.get(InitDataComponent.MAID_INFO);
        if (compoundData != null) {
            CompoundTag maidCompound = compoundData.copyTag();

            var event = new MaidAndItemTransformEvent.ToMaid(maid, stack, maidCompound);
            NeoForge.EVENT_BUS.post(event);

            MaidItemStorageHelper.loadMaid(stack, maid, maidCompound);
            maid.snapTo(context.getClickedPos().above(), 0, 0);
            if (worldIn instanceof ServerLevel) {
                worldIn.addFreshEntity(maid);
            }
            maid.spawnExplosionParticle();
            maid.playSound(SoundEvents.PLAYER_SPLASH, 1.0F, worldIn.getRandom().nextFloat() * 0.1F + 0.9F);
            runnable.run();
            return InteractionResult.SUCCESS;
        } else {
            if (worldIn.isClientSide()) {
                player.sendSystemMessage(Component.translatable("message.touhou_little_maid.photo.have_no_nbt_data"));
            }
        }
        return super.useOn(context);
    }

    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    // 自定义物品实体
    @Override
    public @Nullable Entity createEntity(Level level, Entity location, ItemStack stack) {
        var maid = new EntityMaid(level);
        CustomData compoundData = stack.get(InitDataComponent.MAID_INFO);
        if (compoundData == null) return maid;
        CompoundTag maidCompound = compoundData.copyTag();
        var event = new MaidAndItemTransformEvent.ToMaid(maid, stack, maidCompound);
        NeoForge.EVENT_BUS.post(event);
        MaidItemStorageHelper.loadMaid(stack, maid, maidCompound);
        maid.setPos(location.position());
        return maid;
    }
}
