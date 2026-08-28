package com.github.JumDa5he.callresponse.compat.npc;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/** NPC 日常事件的数据入口；实际内容保存在世界 SavedData，不写入女仆实体。 */
public final class NpcEventData {
    /** 仅用于迁移 2.0.8 初版已经写进女仆的数据，迁移后立即从实体删除。 */
    private static final String LEGACY_ROOT = "CallResponseNpcEvents";
    private static final String CURRENT = "Current";
    private static final String EVENT_TIME = "EventTime";
    private static final String COOLDOWNS = "Cooldowns";
    private static final String PENDING = "Pending";
    private static final String WORK_TICKS = "WorkTicks";
    private static final String MEAL_COUNT = "MealCount";
    private static final String FOOD_TYPES = "FoodTypes";
    private static final String LAST_RANDOM_CHECK = "LastRandomCheck";
    private static final String LAST_DAY = "LastDay";
    private static final String LAST_DREAM_CHECK = "LastDreamCheck";
    private static final String LAST_DREAM_EVENT_DAY = "LastDreamEventDay";
    private static final String LAST_OWNER_INTERACTION = "LastOwnerInteraction";
    private static final String FLAGS = "Flags";
    private static final String FOOD_PROMISE = "FoodPromise";
    private static final String PROMISE_KIND = "Kind";
    private static final String PROMISE_DEADLINE = "Deadline";
    private static final String PROMISE_BASELINE = "Baseline";
    private static final String PROMISE_PREVIOUS_TYPES = "PreviousTypes";
    private static final String OWNER_DAMAGE_WINDOW_START = "OwnerDamageWindowStart";
    private static final String OWNER_DAMAGE_WINDOW_TOTAL = "OwnerDamageWindowTotal";

    private NpcEventData() {
    }

    private static CompoundTag root(EntityMaid maid) {
        NpcEventSavedData savedData = NpcEventSavedData.get(maid);
        CompoundTag persistent = maid.getPersistentData();
        if (persistent.contains(LEGACY_ROOT, Tag.TAG_COMPOUND)) {
            savedData.replace(maid.getUUID(), persistent.getCompound(LEGACY_ROOT));
            persistent.remove(LEGACY_ROOT);
        }
        return savedData.getOrCreate(maid.getUUID());
    }

    private static void changed(EntityMaid maid) {
        NpcEventSavedData.get(maid).setDirty();
    }

    private static CompoundTag child(CompoundTag root, String key) {
        if (!root.contains(key, Tag.TAG_COMPOUND)) root.put(key, new CompoundTag());
        return root.getCompound(key);
    }

    public static String current(EntityMaid maid) {
        return root(maid).getString(CURRENT);
    }

    public static boolean hasCurrent(EntityMaid maid) {
        return !current(maid).isEmpty();
    }

    public static void setCurrent(EntityMaid maid, String id, long gameTime) {
        CompoundTag root = root(maid);
        root.putString(CURRENT, id);
        root.putLong(EVENT_TIME, gameTime);
        changed(maid);
    }

    public static void clearCurrent(EntityMaid maid) {
        CompoundTag root = root(maid);
        root.remove(CURRENT);
        root.remove(EVENT_TIME);
        changed(maid);
    }

    public static long cooldownUntil(EntityMaid maid, String id) {
        return child(root(maid), COOLDOWNS).getLong(id);
    }

    public static void setCooldown(EntityMaid maid, String id, long until) {
        child(root(maid), COOLDOWNS).putLong(id, until);
        changed(maid);
    }

    public static Set<String> pending(EntityMaid maid) {
        Set<String> result = new HashSet<>();
        ListTag list = root(maid).getList(PENDING, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) result.add(list.getString(i));
        return result;
    }

    public static void setPending(EntityMaid maid, Set<String> ids) {
        ListTag list = new ListTag();
        ids.stream().sorted().map(StringTag::valueOf).forEach(list::add);
        root(maid).put(PENDING, list);
        changed(maid);
    }

    public static void addPending(EntityMaid maid, String id) {
        Set<String> ids = pending(maid);
        if (ids.add(id)) setPending(maid, ids);
    }

    public static void removePending(EntityMaid maid, String id) {
        Set<String> ids = pending(maid);
        if (ids.remove(id)) setPending(maid, ids);
    }

    public static long workTicks(EntityMaid maid) {
        return root(maid).getLong(WORK_TICKS);
    }

    public static void addWorkTicks(EntityMaid maid, long amount) {
        CompoundTag root = root(maid);
        root.putLong(WORK_TICKS, Math.max(0L, root.getLong(WORK_TICKS) + amount));
        changed(maid);
    }

    public static void resetWorkTicks(EntityMaid maid) {
        root(maid).putLong(WORK_TICKS, 0L);
        changed(maid);
    }

    public static int mealCount(EntityMaid maid) {
        return root(maid).getInt(MEAL_COUNT);
    }

    public static int foodTypeCount(EntityMaid maid) {
        return root(maid).getList(FOOD_TYPES, Tag.TAG_STRING).size();
    }

    public static void recordFood(EntityMaid maid, ItemStack stack) {
        CompoundTag root = root(maid);
        root.putInt(MEAL_COUNT, root.getInt(MEAL_COUNT) + 1);
        ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null) {
            Set<String> ids = new HashSet<>();
            ListTag old = root.getList(FOOD_TYPES, Tag.TAG_STRING);
            for (int i = 0; i < old.size(); i++) ids.add(old.getString(i));
            if (ids.add(itemId.toString())) {
                ListTag list = new ListTag();
                ids.stream().sorted().map(StringTag::valueOf).forEach(list::add);
                root.put(FOOD_TYPES, list);
            }
        }
        changed(maid);
    }

    public static void resetFoodStats(EntityMaid maid) {
        CompoundTag root = root(maid);
        root.putInt(MEAL_COUNT, 0);
        root.remove(FOOD_TYPES);
        changed(maid);
    }

    /** 保存玩家承诺前的食物数量，后续只认可真正新增到女仆背包的食物。 */
    public static void startFoodPromise(EntityMaid maid, String kind, long deadline) {
        CompoundTag promise = new CompoundTag();
        promise.putString(PROMISE_KIND, kind);
        promise.putLong(PROMISE_DEADLINE, deadline);
        promise.put(PROMISE_BASELINE, foodCounts(maid));
        promise.put(PROMISE_PREVIOUS_TYPES, root(maid).getList(FOOD_TYPES, Tag.TAG_STRING).copy());
        root(maid).put(FOOD_PROMISE, promise);
        changed(maid);
    }

    public static boolean hasFoodPromise(EntityMaid maid) {
        return root(maid).contains(FOOD_PROMISE, Tag.TAG_COMPOUND);
    }

    public static String foodPromiseKind(EntityMaid maid) {
        return root(maid).getCompound(FOOD_PROMISE).getString(PROMISE_KIND);
    }

    public static long foodPromiseDeadline(EntityMaid maid) {
        return root(maid).getCompound(FOOD_PROMISE).getLong(PROMISE_DEADLINE);
    }

    public static ItemStack findNewPromiseFood(EntityMaid maid) {
        CompoundTag baseline = root(maid).getCompound(FOOD_PROMISE).getCompound(PROMISE_BASELINE);
        CompoundTag current = foodCounts(maid);
        for (int slot = 0; slot < maid.getMaidInv().getSlots(); slot++) {
            ItemStack stack = maid.getMaidInv().getStackInSlot(slot);
            ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (!stack.isEmpty() && stack.isEdible() && id != null
                    && current.getInt(id.toString()) > baseline.getInt(id.toString())) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    public static boolean promisePreviouslyAte(EntityMaid maid, ItemStack food) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(food.getItem());
        if (id == null) return false;
        ListTag previous = root(maid).getCompound(FOOD_PROMISE)
                .getList(PROMISE_PREVIOUS_TYPES, Tag.TAG_STRING);
        for (int i = 0; i < previous.size(); i++) {
            if (id.toString().equals(previous.getString(i))) return true;
        }
        return false;
    }

    public static void clearFoodPromise(EntityMaid maid) {
        if (root(maid).contains(FOOD_PROMISE)) {
            root(maid).remove(FOOD_PROMISE);
            changed(maid);
        }
    }

    private static CompoundTag foodCounts(EntityMaid maid) {
        CompoundTag result = new CompoundTag();
        for (int slot = 0; slot < maid.getMaidInv().getSlots(); slot++) {
            ItemStack stack = maid.getMaidInv().getStackInSlot(slot);
            ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (!stack.isEmpty() && stack.isEdible() && id != null) {
                String key = id.toString();
                result.putInt(key, result.getInt(key) + stack.getCount());
            }
        }
        return result;
    }

    public static float addOwnerDamage(EntityMaid maid, long gameTime, float amount, long windowTicks) {
        CompoundTag root = root(maid);
        long start = root.getLong(OWNER_DAMAGE_WINDOW_START);
        if (!root.contains(OWNER_DAMAGE_WINDOW_START, Tag.TAG_LONG)
                || gameTime < start || gameTime - start > windowTicks) {
            start = gameTime;
            root.putLong(OWNER_DAMAGE_WINDOW_START, start);
            root.putFloat(OWNER_DAMAGE_WINDOW_TOTAL, 0.0F);
        }
        float total = root.getFloat(OWNER_DAMAGE_WINDOW_TOTAL) + Math.max(0.0F, amount);
        root.putFloat(OWNER_DAMAGE_WINDOW_TOTAL, total);
        changed(maid);
        return total;
    }

    public static void clearOwnerDamageWindow(EntityMaid maid) {
        CompoundTag root = root(maid);
        root.remove(OWNER_DAMAGE_WINDOW_START);
        root.remove(OWNER_DAMAGE_WINDOW_TOTAL);
        changed(maid);
    }

    public static long lastRandomCheck(EntityMaid maid) {
        return root(maid).getLong(LAST_RANDOM_CHECK);
    }

    public static void setLastRandomCheck(EntityMaid maid, long tick) {
        root(maid).putLong(LAST_RANDOM_CHECK, tick);
        changed(maid);
    }

    public static long lastDay(EntityMaid maid) {
        return root(maid).getLong(LAST_DAY);
    }

    public static void setLastDay(EntityMaid maid, long day) {
        root(maid).putLong(LAST_DAY, day);
        changed(maid);
    }

    public static long lastDreamCheck(EntityMaid maid) {
        return root(maid).getLong(LAST_DREAM_CHECK);
    }

    public static void setLastDreamCheck(EntityMaid maid, long tick) {
        root(maid).putLong(LAST_DREAM_CHECK, tick);
        changed(maid);
    }

    public static long lastDreamEventDay(EntityMaid maid) {
        CompoundTag root = root(maid);
        return root.contains(LAST_DREAM_EVENT_DAY, Tag.TAG_LONG)
                ? root.getLong(LAST_DREAM_EVENT_DAY) : Long.MIN_VALUE;
    }

    public static void setLastDreamEventDay(EntityMaid maid, long day) {
        root(maid).putLong(LAST_DREAM_EVENT_DAY, day);
        changed(maid);
    }

    public static boolean hasLastOwnerInteraction(EntityMaid maid) {
        return root(maid).contains(LAST_OWNER_INTERACTION, Tag.TAG_LONG);
    }

    public static long lastOwnerInteraction(EntityMaid maid) {
        return root(maid).getLong(LAST_OWNER_INTERACTION);
    }

    public static void setLastOwnerInteraction(EntityMaid maid, long tick) {
        root(maid).putLong(LAST_OWNER_INTERACTION, tick);
        changed(maid);
    }

    public static boolean flag(EntityMaid maid, String name) {
        return child(root(maid), FLAGS).getBoolean(name);
    }

    public static void setFlag(EntityMaid maid, String name, boolean value) {
        child(root(maid), FLAGS).putBoolean(name, value);
        changed(maid);
    }

    public static void remove(EntityMaid maid) {
        maid.getPersistentData().remove(LEGACY_ROOT);
        NpcEventSavedData.get(maid).remove(maid.getUUID());
    }
}
