package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.compat.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** Each physical remnant owns two fixed memories in its native 1.21 custom-data component. */
public final class RemnantMemoryItem extends Item {
    public static final int MEMORY_COUNT = 9;
    public static final String MEMORY_TAG = "CallResponseRemnantMemory";
    private static final int SPLIT_BUDGET = 4;

    public RemnantMemoryItem(Properties properties) { super(properties); }

    public static OpenRemnantMemoryS2CPacket choose(RandomSource random) {
        int first = random.nextInt(MEMORY_COUNT);
        int second = random.nextInt(MEMORY_COUNT - 1);
        if (second >= first) second++;
        return new OpenRemnantMemoryS2CPacket(first, second);
    }

    /** Initialize the actual reward before it enters the normal death/drop pipeline. */
    public static ItemStack create(RandomSource random) {
        ItemStack stack = new ItemStack(ModItems.REMNANT_MEMORY.get());
        memories(stack, random);
        return stack;
    }

    /** Server-only. A valid saved pair survives reading, transfer, reload and language changes. */
    public static OpenRemnantMemoryS2CPacket memories(ItemStack stack, RandomSource random) {
        OpenRemnantMemoryS2CPacket existing = savedMemories(stack);
        if (existing != null) return existing;
        OpenRemnantMemoryS2CPacket result = choose(random);
        CompoundTag data = new CompoundTag();
        data.putInt("First", result.first());
        data.putInt("Second", result.second());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(MEMORY_TAG, data));
        return result;
    }

    private static OpenRemnantMemoryS2CPacket savedMemories(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(MEMORY_TAG, Tag.TAG_COMPOUND)) return null;
        CompoundTag saved = data.getCompound(MEMORY_TAG);
        if (saved.contains("First", Tag.TAG_INT) && saved.contains("Second", Tag.TAG_INT)) {
            OpenRemnantMemoryS2CPacket result = new OpenRemnantMemoryS2CPacket(saved.getInt("First"), saved.getInt("Second"));
            if (result.valid()) return result;
        }
        return null;
    }

    private static ItemStack independentSingle(ItemStack legacy, RandomSource random) {
        ItemStack single = legacy.copyWithCount(1);
        // Preserve every other component and custom-data key; excess old units draw separately.
        CustomData.update(DataComponents.CUSTOM_DATA, single, tag -> tag.remove(MEMORY_TAG));
        memories(single, random);
        return single;
    }

    private static void migrateInventory(ServerPlayer player, ItemStack stack) {
        boolean changed = savedMemories(stack) == null;
        memories(stack, player.getRandom());
        for (int i = 0; i < SPLIT_BUDGET && stack.getCount() > 1; i++) {
            ItemStack single = independentSingle(stack, player.getRandom());
            int empty = player.getInventory().getFreeSlot();
            if (empty >= 0) {
                // Inventory.add can delete creative overflow. Use a verified empty slot instead.
                player.getInventory().setItem(empty, single);
            } else {
                ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY() + .5,
                        player.getZ(), single);
                dropped.setDefaultPickUpDelay();
                dropped.setThrower(player);
                if (!player.level().addFreshEntity(dropped) || dropped.isRemoved()) break;
            }
            stack.shrink(1); // A cancelled entity join never consumes the original unit.
            changed = true;
        }
        if (changed) player.getInventory().setChanged();
    }

    @Override public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!level.isClientSide && holder instanceof ServerPlayer player) migrateInventory(player, stack);
    }

    @Override public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level().isClientSide) return false;
        if (stack.getCount() == 1 && savedMemories(stack) != null) return false;
        memories(stack, entity.level().getRandom());
        for (int i = 0; i < SPLIT_BUDGET && stack.getCount() > 1; i++) {
            ItemEntity single = entity.copy(); // Fresh UUID; native age, lifespan and position.
            CompoundTag dropState = new CompoundTag();
            entity.addAdditionalSaveData(dropState);
            single.readAdditionalSaveData(dropState); // Retain pickup delay and owner/thrower restrictions.
            single.setItem(independentSingle(stack, entity.level().getRandom()));
            single.setNoGravity(entity.isNoGravity());
            single.setDeltaMovement(entity.getDeltaMovement());
            if (!entity.level().addFreshEntity(single) || single.isRemoved()) break;
            stack.shrink(1);
        }
        entity.setItem(stack.copy()); // Make the component/count update visible to entity tracking.
        return false;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer recipient) {
            ItemStack stack = player.getItemInHand(hand);
            migrateInventory(recipient, stack);
            recipient.containerMenu.broadcastChanges();
            PacketDistributor.sendToPlayer(recipient, memories(stack, recipient.getRandom()));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
