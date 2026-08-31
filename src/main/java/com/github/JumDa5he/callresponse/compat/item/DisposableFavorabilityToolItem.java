package com.github.JumDa5he.callresponse.compat.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;


public final class DisposableFavorabilityToolItem extends Item {
    public DisposableFavorabilityToolItem(Identifier id) {
        super(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(64));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
                                                   LivingEntity target, InteractionHand hand) {
        if (!(target instanceof EntityMaid maid) || hand != InteractionHand.MAIN_HAND
                || !maid.isOwnedBy(player)) {
            return InteractionResult.PASS;
        }

        int point = player.isShiftKeyDown() ? 1 : 64;
        maid.getFavorabilityManager().add(point);
        maid.playSound(SoundEvents.PLAYER_LEVELUP, 0.5F,
                maid.getRandom().nextFloat() * 0.1F + 0.9F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return (player.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                TooltipDisplay display, Consumer<Component> components, TooltipFlag flag) {
        components.accept(Component.translatable("tooltips.touhou_little_maid.favorability_tool.add")
                .withStyle(ChatFormatting.GRAY));
    }
}
