package com.github.JumDa5he.callresponse.compat.winefox;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.item.ModItems;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

/** Each physical remnant owns its two memories. Reading only initializes old/malformed items once. */
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

    /** Death rewards receive their contents before entering the ordinary loot/drop pipeline. */
    public static ItemStack create(RandomSource random) {
        ItemStack stack = new ItemStack(ModItems.REMNANT_MEMORY.get());
        memories(stack, random);
        return stack;
    }

    /** Call only on the server. Valid saved IDs are never rerolled, even after a language change. */
    public static OpenRemnantMemoryS2CPacket memories(ItemStack stack, RandomSource random) {
        OpenRemnantMemoryS2CPacket existing = savedMemories(stack);
        if (existing != null) return existing;
        OpenRemnantMemoryS2CPacket result = choose(random);
        CompoundTag data = new CompoundTag();
        data.putInt("First", result.first());
        data.putInt("Second", result.second());
        stack.getOrCreateTag().put(MEMORY_TAG, data);
        return result;
    }

    private static OpenRemnantMemoryS2CPacket savedMemories(ItemStack stack) {
        CompoundTag saved = stack.getTagElement(MEMORY_TAG);
        if (saved != null && saved.contains("First", Tag.TAG_INT) && saved.contains("Second", Tag.TAG_INT)) {
            OpenRemnantMemoryS2CPacket result = new OpenRemnantMemoryS2CPacket(saved.getInt("First"), saved.getInt("Second"));
            if (result.valid()) return result;
        }
        return null;
    }

    private static ItemStack independentSingle(ItemStack legacy, RandomSource random) {
        ItemStack single = legacy.copy();
        single.setCount(1);
        // A legacy oversized stack did not represent one shared story. Each excess physical
        // item is assigned separately, while the original stack's already-read pair stays fixed.
        single.removeTagKey(MEMORY_TAG);
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
                // Inventory.add intentionally deletes overflow in creative mode. Use only a
                // verified empty slot so old quantities are preserved in every game mode.
                player.getInventory().setItem(empty, single);
            } else {
                ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY() + .5,
                        player.getZ(), single);
                dropped.setDefaultPickUpDelay();
                dropped.setThrower(player.getUUID());
                if (!player.level().addFreshEntity(dropped) || dropped.isRemoved()) break;
            }
            // A cancelled item-entity join leaves the original quantity in the old stack.
            stack.shrink(1);
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
            ItemEntity single = entity.copy(); // Fresh UUID; native copy retains the original drop age/lifespan.
            CompoundTag dropState = new CompoundTag();
            entity.addAdditionalSaveData(dropState);
            single.readAdditionalSaveData(dropState); // Preserve pickup delay and thrower/owner restrictions.
            single.setItem(independentSingle(stack, entity.level().getRandom()));
            single.setNoGravity(entity.isNoGravity());
            single.setDeltaMovement(entity.getDeltaMovement());
            if (!entity.level().addFreshEntity(single) || single.isRemoved()) break;
            stack.shrink(1);
        }
        // SynchedEntityData must see a new stack object after an in-place NBT/count update.
        entity.setItem(stack.copy());
        return false;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer recipient) {
            ItemStack stack = player.getItemInHand(hand);
            migrateInventory(recipient, stack);
            recipient.containerMenu.broadcastChanges();
            CallResponseMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient), memories(stack, recipient.getRandom()));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
