package com.github.JumDa5he.callresponse.compat.disguise;

import com.github.JumDa5he.callresponse.compat.outpost.BetrayalOutpostSavedData;
import com.github.JumDa5he.callresponse.mixin.accessor.OutpostLootContainerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Original camp loot is snapshotted exactly once on its first genuine player opening. */
public final class OutpostLootAccessManager {
    private static final Map<UUID, PendingOpen> PENDING = new ConcurrentHashMap<>();

    private record PendingLoot(BlockPos pos, boolean pendingNativeLoot) {}
    private record PendingOpen(String dimension, BlockPos pos, long tick, List<PendingLoot> loot) {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
        BetrayalOutpostSavedData.LootChest chest = data.lootChest(level, pos);
        if (chest == null) return;
        // Legacy single-roll doubles alias one half; new doubles may own two independent rolls.
        BlockEntity blockEntity = level.getBlockEntity(chest.pos());
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) return;
        BetrayalOutpostSavedData.Outpost camp = data.findByKey(level, chest.campKey());
        if (camp != null && isApproved(player)) {
            OutpostDisguiseRelations.clearOldTargetsNear(player);
        }
        List<PendingLoot> loot = new ArrayList<>();
        capturePending(level, chest, loot);
        var state = level.getBlockState(pos);
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockPos other = pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
            var companion = data.lootChest(level, other);
            if (companion != null && !companion.pos().equals(chest.pos())) capturePending(level, companion, loot);
        }
        PENDING.put(player.getUUID(), new PendingOpen(level.dimension().location().toString(),
                chest.pos(), level.getGameTime(), List.copyOf(loot)));
    }

    private static void capturePending(ServerLevel level, BetrayalOutpostSavedData.LootChest chest,
                                       List<PendingLoot> loot) {
        if (!(level.getBlockEntity(chest.pos()) instanceof RandomizableContainerBlockEntity container)) return;
        OutpostLootContainerAccessor access = (OutpostLootContainerAccessor) container;
        loot.add(new PendingLoot(chest.pos(), chest.lootTable().equals(access.callresponse$getLootTable())
                && chest.lootSeed() == access.callresponse$getLootTableSeed()));
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !(event.getContainer() instanceof ChestMenu)) return;
        PendingOpen pending = PENDING.remove(player.getUUID());
        if (pending == null || !pending.dimension().equals(level.dimension().location().toString())
                || level.getGameTime() - pending.tick() > 2) return;
        BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
        BetrayalOutpostSavedData.LootChest chest = data.lootChest(level, pending.pos());
        if (chest == null) return;
        BlockEntity blockEntity = level.getBlockEntity(pending.pos());
        if (!(blockEntity instanceof RandomizableContainerBlockEntity container)) return;
        BetrayalOutpostSavedData.Outpost camp = data.findByKey(level, chest.campKey());
        if (camp == null) return;

        boolean approved = isApproved(player);
        if (approved) OutpostDisguiseRelations.clearOldTargetsNear(player);
        else if (DisguiseManager.isActive(player)) OutpostDisguiseRelations.expose(player, camp);
        for (PendingLoot roll : pending.loot()) settleRoll(level, player, data, roll, approved);
    }

    private static void settleRoll(ServerLevel level, ServerPlayer player, BetrayalOutpostSavedData data,
                                   PendingLoot pending, boolean approved) {
        var chest = data.lootChest(level, pending.pos());
        if (chest == null || !(level.getBlockEntity(chest.pos()) instanceof RandomizableContainerBlockEntity container)) return;
        if (data.isChestSettled(chest)) return;

        // Commit before giving items. Reopening, another player, and a later block replacement cannot repeat it.
        data.settleChest(chest);
        if (!pending.pendingNativeLoot()) return; // pre-opened, hopper-unpacked or legacy chest: no guessed bonus
        OutpostLootContainerAccessor access = (OutpostLootContainerAccessor) container;
        if (access.callresponse$getLootTable() != null) access.callresponse$unpackLootTable(player);
        if (!approved || chest.lootTable().getPath().endsWith("betrayal_outpost_story")) return;

        List<ItemStack> original = new ArrayList<>();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() != Items.WRITTEN_BOOK
                    && stack.getItem() != Items.WRITABLE_BOOK) original.add(stack.copy());
        }
        if (!original.isEmpty()) {
            for (ItemStack stack : original) addOrDrop(level, pending.pos(), container, stack);
            container.setChanged();
            player.displayClientMessage(Component.translatable("message.callresponse.outpost.loot_bonus"), true);
        }
    }

    private static boolean isApproved(ServerPlayer player) {
        return DisguiseManager.isRecognized(player);
    }

    private static void addOrDrop(ServerLevel level, BlockPos pos,
                                  RandomizableContainerBlockEntity container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, stack)) continue;
            int move = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), container.getMaxStackSize()));
            ItemStack inserted = stack.copy();
            inserted.setCount(move);
            container.setItem(slot, inserted);
            stack.shrink(move);
        }
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            ItemStack present = container.getItem(slot);
            if (present.isEmpty() || !ItemStack.isSameItemSameTags(present, stack)) continue;
            int move = Math.min(stack.getCount(), Math.min(present.getMaxStackSize(), container.getMaxStackSize())
                    - present.getCount());
            if (move <= 0) continue;
            present.grow(move);
            stack.shrink(move);
            container.setItem(slot, present);
        }
        if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5D,
                pos.getY() + 1.0D, pos.getZ() + 0.5D, stack);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
        BetrayalOutpostSavedData.LootChest chest = data.lootChest(level, event.getPos());
        if (chest == null) return;
        if (event.getPlayer() instanceof ServerPlayer player) {
            BetrayalOutpostSavedData.Outpost camp = data.findByKey(level, chest.campKey());
            if (camp != null && DisguiseManager.isActive(player)
                    && !isApproved(player)) {
                OutpostDisguiseRelations.expose(player, camp);
            }
        }
        data.removeChest(chest); // breaking gives no second bonus path
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BetrayalOutpostSavedData data = BetrayalOutpostSavedData.get(level);
        BetrayalOutpostSavedData.LootChest chest = data.lootChest(level, event.getPos());
        if (chest != null) data.removeChest(chest);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        PendingOpen pending = PENDING.get(player.getUUID());
        if (pending != null && player.level().getGameTime() - pending.tick() > 2) {
            PENDING.remove(player.getUUID(), pending);
        }
    }
}
