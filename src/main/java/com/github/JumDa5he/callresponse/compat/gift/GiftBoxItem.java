package com.github.JumDa5he.callresponse.compat.gift;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 礼物盒：手持使用打开，拆出的东西与效果由组件里的 {@link GiftBoxData} 决定。
 */
public final class GiftBoxItem extends Item {

    public GiftBoxItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        GiftBoxData data = readData(stack);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        GiftService.open(serverPlayer, data);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private static GiftBoxData readData(ItemStack stack) {
        GiftBoxData data = stack.get(ModGiftComponents.GIFT_BOX.get());
        return data == null ? GiftBoxData.EMPTY : data;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GiftBoxData data = stack.get(ModGiftComponents.GIFT_BOX.get());
        if (data != null && !data.maidName().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.callresponse.gift_box.from", data.maidName())
                    .withStyle(ChatFormatting.GRAY));
        }
        // 按玩家实际键位显示，别写死「右键」
        String useKey = "右键";
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            useKey = com.github.JumDa5he.callresponse.compat.gift.client.GiftClientHints.useKeyName();
        }
        tooltip.add(Component.translatable("tooltip.callresponse.gift_box.hint", useKey)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.callresponse.gift_box.return_hint", useKey)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
