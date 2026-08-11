package com.github.JumDa5he.callresponse.compat.hunt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 狩猎令名单数据：存储在女仆自身的 PersistentData 中（上限 10 条）。
 * 狩猎令物品只是"钥匙"，名单归属具体女仆。
 */
public final class HuntOrderData {

    public static final String HUNT_TAG = "CallResponseHuntOrder";
    public static final String LIST_TAG = "Entries";
    public static final int MAX_ENTRIES = 10;

    private HuntOrderData() {
    }

    public static List<HuntOrderEntry> getEntries(EntityMaid maid) {
        List<HuntOrderEntry> result = new ArrayList<>();
        CompoundTag data = maid.getPersistentData();
        if (!data.contains(HUNT_TAG, Tag.TAG_COMPOUND)) {
            return result;
        }
        CompoundTag hunt = data.getCompound(HUNT_TAG);
        if (!hunt.contains(LIST_TAG, Tag.TAG_LIST)) {
            return result;
        }
        ListTag list = hunt.getList(LIST_TAG, Tag.TAG_COMPOUND);
        for (Tag tag : list) {
            if (tag instanceof CompoundTag compound) {
                result.add(new HuntOrderEntry(compound));
            }
        }
        return result;
    }

    public static boolean hasEntries(EntityMaid maid) {
        return !getEntries(maid).isEmpty();
    }

    public static boolean contains(EntityMaid maid, UUID uuid) {
        for (HuntOrderEntry entry : getEntries(maid)) {
            if (entry.uuid.equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 添加条目。返回 true 表示成功（未超上限且未重复）。
     */
    public static boolean addEntry(EntityMaid maid, UUID uuid, String name, boolean isPlayer) {
        if (contains(maid, uuid)) {
            return false;
        }
        List<HuntOrderEntry> entries = getEntries(maid);
        if (entries.size() >= MAX_ENTRIES) {
            return false;
        }
        entries.add(new HuntOrderEntry(uuid, name, isPlayer));
        save(maid, entries);
        return true;
    }

    /**
     * 删除条目。返回 true 表示存在并已删除。
     */
    public static boolean removeEntry(EntityMaid maid, UUID uuid) {
        List<HuntOrderEntry> entries = getEntries(maid);
        boolean removed = entries.removeIf(e -> e.uuid.equals(uuid));
        if (removed) {
            save(maid, entries);
        }
        return removed;
    }

    public static void removeSelf(EntityMaid maid) {
        removeEntry(maid, maid.getUUID());
    }

    private static void save(EntityMaid maid, List<HuntOrderEntry> entries) {
        CompoundTag data = maid.getPersistentData();
        CompoundTag hunt = new CompoundTag();
        ListTag list = new ListTag();
        for (HuntOrderEntry entry : entries) {
            list.add(entry.save());
        }
        hunt.put(LIST_TAG, list);
        data.put(HUNT_TAG, hunt);
    }
}
