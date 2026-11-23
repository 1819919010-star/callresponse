package com.github.JumDa5he.callresponse.compat.winefox.client;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntities;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WineFoxClientEvents {
    private static final List<WeakReference<WineFoxRenderer>> RENDERERS = new ArrayList<>();

    static void track(WineFoxRenderer renderer) { RENDERERS.add(new WeakReference<>(renderer)); }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WineFoxEntities.FOX.get(), WineFoxRenderer::new);
        event.registerEntityRenderer(WineFoxEntities.SPIKE.get(), WineFoxSpikeRenderer::new);
    }

    @SubscribeEvent
    public static void resources(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new WineFoxModelResources());
        event.registerReloadListener(new WineFoxSurrenderAnimations());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void itemColors(RegisterColorHandlersEvent.Item event) {
        // Only this egg has a complete coloured texture, independent of vanilla's two tint layers.
        event.register((stack, tint) -> -1, ModItems.WINE_FOX_SPAWN_EGG.get());
    }

    @SubscribeEvent
    public static void drinkableOnly(BuildCreativeModeTabContentsEvent event) {
        // Vanilla automatically enumerates every potion for all bottle/arrow types.
        // This feature offers only the two drinkable bottles, including in search tabs.
        for (var potion : List.of(WineFoxPotions.MALICE.get(), WineFoxPotions.DESSERT.get()))
            for (var item : List.of(Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW))
                event.getEntries().remove(PotionUtils.setPotion(new net.minecraft.world.item.ItemStack(item), potion));
    }

    @Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT)
    public static final class Lifecycle {
        @SubscribeEvent
        public static void potionTooltip(ItemTooltipEvent event) {
            if (!WineFoxPotions.isCustomPotion(event.getItemStack())) return;
            String name = PotionUtils.getPotion(event.getItemStack()) == WineFoxPotions.MALICE.get() ? "malice" : "dessert";
            for (int i = 1; i <= 3; i++) event.getToolTip().add(Component.translatable(
                    "tooltip.callresponse." + name + "." + i).withStyle(ChatFormatting.GRAY));
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            RENDERERS.removeIf(reference -> reference.get() == null);
            for (var reference : RENDERERS) {
                WineFoxRenderer renderer = reference.get();
                if (renderer != null) renderer.pruneAnimations();
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            for (var reference : RENDERERS) {
                WineFoxRenderer renderer = reference.get();
                if (renderer != null) renderer.clearAnimations();
            }
        }
    }
}
