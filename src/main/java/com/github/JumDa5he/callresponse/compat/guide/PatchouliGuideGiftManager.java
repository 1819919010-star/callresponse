package com.github.JumDa5he.callresponse.compat.guide;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.lang.reflect.Method;

/** Patchouli 存在时向每位玩家赠送一次手册；未安装时不产生任何物品或硬链接。 */
@EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class PatchouliGuideGiftManager {
    private static final String GIFTED_TAG = "CallResponseLoveAndLoatheGuideGifted";
    private static final ResourceLocation BOOK_ID =
            ResourceLocation.fromNamespaceAndPath(CallResponseMod.MOD_ID, "love_and_loathe");

    private PatchouliGuideGiftManager() {
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ModList.get().isLoaded("patchouli")) {
            return;
        }
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIFTED_TAG)) {
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
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static ItemStack createGuideBook() {
        try {
            Class<?> apiClass = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Object api = apiClass.getMethod("get").invoke(null);
            Method getBookStack = api.getClass().getMethod("getBookStack", ResourceLocation.class);
            return (ItemStack) getBookStack.invoke(api, BOOK_ID);
        } catch (ReflectiveOperationException | LinkageError exception) {
            CallResponseMod.LOGGER.warn("无法创建帕秋莉手册 {}", BOOK_ID, exception);
            return ItemStack.EMPTY;
        }
    }
}
