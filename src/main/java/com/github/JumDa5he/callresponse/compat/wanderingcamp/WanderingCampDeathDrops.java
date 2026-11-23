package com.github.JumDa5he.callresponse.compat.wanderingcamp;

import com.github.tartaricacid.touhoulittlemaid.entity.backpack.BackpackManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Called at TLM's committed equipment-drop stage, inside NeoForge's normal captured-drop flow. */
public final class WanderingCampDeathDrops {
    private WanderingCampDeathDrops() {}

    public static void transfer(EntityMaid maid) {
        var data = WanderingCampData.data(maid);
        if (maid.level().isClientSide || !WanderingCampData.active(maid) || !((CampMaid)maid).callresponse$campDeathCommitted()
                || data.getBoolean("InventoryDropped")) return;
        data.putBoolean("InventoryDropped", true);
        transferCommittedInventory(maid);
    }

    /** Shared disjoint real-slot transfer; the caller owns its once-only death marker. */
    public static void transferCommittedInventory(EntityMaid maid) {
        if (maid.level().isClientSide || !((CampMaid) maid).callresponse$campDeathCommitted()) return;
        // Health <= 0 alone is insufficient: a canceled LivingDeathEvent may leave 0 HP.
        // LivingEntity.dead is set only after the cancellable death hook accepts the death.
        // Native BigBackpack only expands maidInv (its actual handler slots). It does NOT contain those
        // slots in item NBT. Preserve the native take-off item, without invoking onTakeOff,
        // which would independently drop the very same extended slots a second time.
        ItemStack backpack = maid.getMaidBackpackType().getTakeOffItemStack(ItemStack.EMPTY, null, maid).copy();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = maid.getItemBySlot(slot);
            maid.setItemSlot(slot, ItemStack.EMPTY);
            drop(maid, stack);
        }
        // Raw disjoint handlers, NOT getAvailableInv/getAvailableBackpackInv (aliased/ranged views).
        drain(maid, maid.getMaidInv());
        drain(maid, maid.getMaidBauble());
        drain(maid, maid.getHideInv());
        drain(maid, maid.getTaskInv());
        maid.setMaidBackpackType(BackpackManager.getEmptyBackpack());
        drop(maid, backpack);
    }

    private static void drain(EntityMaid maid, IItemHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++)
            drop(maid, inventory.extractItem(slot, Integer.MAX_VALUE, false));
    }
    private static void drop(EntityMaid maid, ItemStack stack) {
        if (!stack.isEmpty()) maid.spawnAtLocation(stack);
    }
}
