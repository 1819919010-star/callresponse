package com.github.JumDa5he.callresponse.compat.block;

import com.github.JumDa5he.callresponse.compat.menu.RewardBoxMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class RewardBoxBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SIZE = 108;
    private final ItemStacksResourceHandler itemHandler = new ItemStacksResourceHandler(SIZE) {
        @Override
        protected void onContentsChanged(int index, ItemStack previousContents) {
            RewardBoxBlockEntity.this.setChanged();
        }
    };
    private UUID ownerId;

    public RewardBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.REWARD_BOX_ENTITY.get(), pos, state);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID value) {
        ownerId = value;
        setChanged();
    }

    public boolean mayOpen(Player player) {
        return ownerId == null || ownerId.equals(player.getUUID());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("Owner", UUIDUtil.CODEC, ownerId);
        output.putChild("Items", itemHandler);
    }

    @Override
    public void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ownerId = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        input.readChild("Items", itemHandler);
    }

    public ItemStacksResourceHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < itemHandler.size(); slot++) {
            if (!itemHandler.getResource(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemUtil.getStack(itemHandler, slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack current = getItem(slot);
        if (current.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = current.split(Math.min(count, current.getCount()));
        setItem(slot, current);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        itemHandler.set(slot, ItemResource.EMPTY, 0);
        return current;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (stack.getCount() > getMaxStackSize()) {
            stack = stack.copyWithCount(getMaxStackSize());
        }
        itemHandler.set(slot, ItemResource.of(stack), stack.isEmpty() ? 0 : stack.getCount());
    }

    @Override
    public boolean stillValid(Player player) {
        return mayOpen(player)
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < itemHandler.size(); slot++) {
            itemHandler.set(slot, ItemResource.EMPTY, 0);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.callresponse.reward_box");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return mayOpen(player) ? new RewardBoxMenu(id, inventory, this) : null;
    }
}
