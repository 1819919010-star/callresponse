package com.github.JumDa5he.callresponse.compat.guide;

import com.github.JumDa5he.callresponse.CallResponseMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Patchouli 存在时向每位玩家赠送一次手册；未安装时不产生任何物品或硬链接。 */
@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class PatchouliGuideGiftManager {
    private static final String GIFTED_TAG = "CallResponseLoveAndLoatheGuideGifted";
    private static final ResourceLocation GUIDE_BOOK_ITEM =
            ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book");
    private static final String BOOK_ID = "callresponse:love_and_loathe";

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
        Item guideBook = ForgeRegistries.ITEMS.getValue(GUIDE_BOOK_ITEM);
        if (guideBook == null) {
            return;
        }
        ItemStack stack = new ItemStack(guideBook);
        stack.getOrCreateTag().putString("patchouli:book", BOOK_ID);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        persisted.putBoolean(GIFTED_TAG, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
