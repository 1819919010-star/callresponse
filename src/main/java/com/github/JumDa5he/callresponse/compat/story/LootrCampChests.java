package com.github.JumDa5he.callresponse.compat.story;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostManager;
import com.github.JumDa5he.callresponse.compat.wanderingcamp.WanderingCampStory;
import com.github.JumDa5he.callresponse.mixin.accessor.OutpostLootContainerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = CallResponseMod.MOD_ID)
public final class LootrCampChests {
    private LootrCampChests() {}

    /** Lootr keeps its own table after the vanilla pending table has been cleared. */
    @Nullable
    public static ResourceLocation table(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof RandomizableContainerBlockEntity chest)) return null;
        ResourceLocation type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(chest.getType());
        if (type == null || !"lootr".equals(type.getNamespace())) return null;
        ResourceLocation pending = ((OutpostLootContainerAccessor) chest).callresponse$getLootTable();
        if (pending != null) return pending;
        // Both Forge 1.20 Lootr 0.7.35.90 and .94 serialize their saved table in LootTable.
        String saved = chest.saveWithoutMetadata().getString("LootTable");
        return saved.isEmpty() ? null : ResourceLocation.tryParse(saved);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOpen(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ModList.get().isLoaded("lootr")) return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity chest)) return;
        // A vanilla story chest can still be queued for conversion by Lootr this tick.
        ResourceLocation id = ((OutpostLootContainerAccessor) chest).callresponse$getLootTable();
        if (id == null) id = table(chest);
        boolean pending = CampStoryBooks.WANDERING_TABLE.equals(id) && WanderingCampStory.isStoryPending(level, pos)
                || CampStoryBooks.OUTPOST_TABLE.equals(id) && BetrayalOutpostManager.isStoryPending(level, pos);
        if (pending) {
            event.setCancellationResult(InteractionResult.CONSUME);
            event.setCanceled(true);
        }
    }
}
