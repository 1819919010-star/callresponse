package com.github.JumDa5he.callresponse.compat.birthday;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.DateTimeException;
import java.time.Month;
import java.time.MonthDay;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家生日的全局存档数据。
 *
 * <p>生日按玩家 UUID 存放于主世界存档中，与维度、女仆和单次登录无关，
 * 属于「隐藏配置」：玩家只能通过生日贺卡查看/修改自己的记录。</p>
 */
public final class BirthdayData extends SavedData {
    public static final String DATA_NAME = "callresponse_birthday";

    /** 单个玩家的生日记录：月、日，以及最近一次庆祝的年份（避免同一天重复庆祝）。 */
    public record Entry(int month, int day, int lastCelebratedYear) {
        public MonthDay monthDay() {
            return MonthDay.of(month, day);
        }
    }

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static BirthdayData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BirthdayData::new, BirthdayData::load, null),
                DATA_NAME);
    }

    public static BirthdayData load(CompoundTag tag, HolderLookup.Provider provider) {
        BirthdayData data = new BirthdayData();
        ListTag list = tag.getList("Birthdays", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Player")) {
                continue;
            }
            int month = entry.getInt("Month");
            int day = entry.getInt("Day");
            if (!isValid(month, day)) {
                continue;
            }
            data.entries.put(entry.getUUID("Player"),
                    new Entry(month, day, entry.getInt("LastCelebratedYear")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        entries.forEach((player, entry) -> {
            CompoundTag compound = new CompoundTag();
            compound.putUUID("Player", player);
            compound.putInt("Month", entry.month());
            compound.putInt("Day", entry.day());
            compound.putInt("LastCelebratedYear", entry.lastCelebratedYear());
            list.add(compound);
        });
        tag.put("Birthdays", list);
        return tag;
    }

    /** 用于校验任意来源的月/日取值，非法组合（如 2 月 30 日）会被拒绝。 */
    public static boolean isValid(int month, int day) {
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return false;
        }
        try {
            MonthDay.of(month, day);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }

    public static int daysInMonth(int month) {
        if (month < 1 || month > 12) {
            return 0;
        }
        // 用非闰年的 2 月长度做静态上限校验，2 月 29 日单独放行。
        Month value = Month.of(month);
        return value == Month.FEBRUARY ? 29 : value.maxLength();
    }

    public Entry entry(UUID player) {
        return entries.get(player);
    }

    public MonthDay birthday(UUID player) {
        Entry entry = entries.get(player);
        return entry == null ? null : entry.monthDay();
    }

    public void setBirthday(UUID player, int month, int day) {
        if (!isValid(month, day)) {
            return;
        }
        int lastYear = entries.containsKey(player) ? entries.get(player).lastCelebratedYear() : 0;
        entries.put(player, new Entry(month, day, lastYear));
        setDirty();
    }

    public void clear(UUID player) {
        if (entries.remove(player) != null) {
            setDirty();
        }
    }

    public int lastCelebratedYear(UUID player) {
        Entry entry = entries.get(player);
        return entry == null ? 0 : entry.lastCelebratedYear();
    }

    public void markCelebrated(UUID player, int year) {
        Entry entry = entries.get(player);
        if (entry == null || entry.lastCelebratedYear() == year) {
            return;
        }
        entries.put(player, new Entry(entry.month(), entry.day(), year));
        setDirty();
    }
}
