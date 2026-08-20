package com.github.JumDa5he.callresponse.compat.menu;

import com.github.JumDa5he.callresponse.compat.block.RewardBoxBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class RewardBoxMenu extends AbstractContainerMenu {
    private final Container container;

    public RewardBoxMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(RewardBoxBlockEntity.SIZE)); }
    public RewardBoxMenu(int id, Inventory inventory, Container container) {
        super(ModMenus.REWARD_BOX.get(), id);
        this.container = container;
        checkContainerSize(container, RewardBoxBlockEntity.SIZE);
        container.startOpen(inventory.player);
        for (int row = 0; row < 9; row++) for (int col = 0; col < 12; col++) addSlot(new Slot(container, col + row * 12, 8 + col * 18, 18 + row * 18));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col + row * 9 + 9, 35 + col * 18, 184 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 35 + col * 18, 242));
    }

    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); container.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack copy = slot.getItem().copy(), moving = slot.getItem();
        if (index < RewardBoxBlockEntity.SIZE) {
            if (!moveItemStackTo(moving, RewardBoxBlockEntity.SIZE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(moving, 0, RewardBoxBlockEntity.SIZE, false)) return ItemStack.EMPTY;
        if (moving.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
}
