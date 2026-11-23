package com.github.JumDa5he.callresponse.compat.winefox.client;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxEntities;
import com.github.JumDa5he.callresponse.compat.winefox.WineFoxPotions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.bus.api.EventPriority;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
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
        for (var potion : List.of(WineFoxPotions.MALICE, WineFoxPotions.DESSERT))
            for (var item : List.of(Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW))
                event.remove(PotionContents.createItemStack(item, potion), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    @EventBusSubscriber(modid = CallResponseMod.MOD_ID, value = Dist.CLIENT)
    public static final class Lifecycle {
        @SubscribeEvent
        public static void potionTooltip(ItemTooltipEvent event) {
            if (!WineFoxPotions.isCustomPotion(event.getItemStack())) return;
            String name = event.getItemStack().getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY)
                    .potion().map(potion -> potion.is(WineFoxPotions.MALICE)).orElse(false) ? "malice" : "dessert";
            for (int i = 1; i <= 3; i++) event.getToolTip().add(Component.translatable(
                    "tooltip.callresponse." + name + "." + i).withStyle(ChatFormatting.GRAY));
        }

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
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
