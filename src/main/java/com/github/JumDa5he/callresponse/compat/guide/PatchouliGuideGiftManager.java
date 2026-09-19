package com.github.JumDa5he.callresponse.compat.guide;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.lang.reflect.Method;

/** Patchouli 存在时向每位玩家赠送一次手册；未安装时不产生任何物品或硬链接。 */
@EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class PatchouliGuideGiftManager {
    private static final String GIFTED_TAG = "CallResponseLoveAndLoatheGuideGifted";
    private static final ResourceLocation GUIDE_BOOK_ITEM =
            ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book");
    private static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "love_and_loathe");

    private PatchouliGuideGiftManager() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ModList.get().isLoaded("patchouli")) {
            return;
        }
        CompoundTag persisted = player.getPersistentData();
        if (persisted.getBoolean(GIFTED_TAG)) {
            return;
        }
        Item guideBook = BuiltInRegistries.ITEM.get(GUIDE_BOOK_ITEM);
        if (guideBook == Items.AIR) {
            return;
        }
        ItemStack stack = createGuideBook();
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        persisted.putBoolean(GIFTED_TAG, true);
    }

    /** Patchouli 1.21.1 使用数据组件记录书籍编号，这里通过反射保持可选依赖。 */
    private static ItemStack createGuideBook() {
        try {
            Class<?> itemClass = Class.forName("vazkii.patchouli.common.item.ItemModBook");
            Method forBook = itemClass.getMethod("forBook", ResourceLocation.class);
            Object result = forBook.invoke(null, BOOK_ID);
            return result instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | LinkageError error) {
            CallResponseMod.LOGGER.warn("Unable to create Patchouli guide book stack", error);
            return ItemStack.EMPTY;
        }
    }
}
