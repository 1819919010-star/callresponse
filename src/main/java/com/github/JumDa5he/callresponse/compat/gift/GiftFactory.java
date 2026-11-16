package com.github.JumDa5he.callresponse.compat.gift;

import com.github.JumDa5he.callresponse.compat.emotion.EmotionData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * 按女仆当前情感状态摇出一份礼物。
 */
public final class GiftFactory {
    private GiftFactory() {
    }

    public static GiftBoxData roll(EntityMaid maid, UUID ownerId) {
        EmotionData.EmotionValues values = EmotionData.get(maid, ownerId);
        EmotionData.EmotionTendency tendency = EmotionData.getTendency(maid, ownerId);
        GiftKind kind = GiftTendencyProfile.roll(tendency, values, maid.getRandom());

        String maidName = maid.getCustomName() != null
                ? maid.getCustomName().getString()
                : Component.translatable("gift.callresponse.unknown_maid").getString();
        return GiftBoxData.of(kind, maid.getUUID(), maidName, maid.getModelId());
    }

    public static ItemStack createBox(GiftBoxData data) {
        ItemStack stack = new ItemStack(com.github.JumDa5he.callresponse.compat.item.ModItems.GIFT_BOX.get());
        stack.set(ModGiftComponents.GIFT_BOX.get(), data);
        return stack;
    }
}
