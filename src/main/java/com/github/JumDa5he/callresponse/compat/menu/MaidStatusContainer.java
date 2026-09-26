package com.github.JumDa5he.callresponse.compat.menu;

import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MaidStatusContainer extends AbstractMaidContainer {
    private static final int MAIN_INVENTORY_SIZE = 27;

    public MaidStatusContainer(int id, Inventory inventory, int entityId) {
        super(ModMenus.MAID_STATUS.get(), id, inventory, entityId);
    }

    public static MenuProvider create(int entityId) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Maid Status");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new MaidStatusContainer(id, inventory, entityId);
            }
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem().copy();
        ItemStack moving = slot.getItem();
        if (index < MAIN_INVENTORY_SIZE) {
            if (!moveItemStackTo(moving, MAIN_INVENTORY_SIZE, slots.size(), false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(moving, 0, MAIN_INVENTORY_SIZE, false)) {
            return ItemStack.EMPTY;
        }
        if (moving.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
