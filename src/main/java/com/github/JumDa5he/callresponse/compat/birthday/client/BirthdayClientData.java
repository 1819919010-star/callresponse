package com.github.JumDa5he.callresponse.compat.birthday.client;

import com.github.JumDa5he.callresponse.CallResponseMod;
import com.github.JumDa5he.callresponse.compat.birthday.BirthdayData;
import com.github.JumDa5he.callresponse.compat.birthday.BirthdayDates;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * 客户端缓存的本地玩家生日信息（由服务端 {@code BirthdaySyncS2CPacket} 下发）。
 *
 * <p>使用 {@link #isBirthdayToday()} 即可判断当前客户端玩家的生日是不是今天。
 * 在线时使用服务端日期（与服务端触发庆祝的口径一致），离线场景（标题界面等）
 * 退回本机日期，因此标题界面彩蛋也能正常工作。</p>
 *
 * <p>生日会持久化到 {@code config/callresponse-birthday-client.json}，
 * 这样重开游戏后即使还没进入服务器也能拿到自己的生日。</p>
 */
public final class BirthdayClientData {
    private static final String FILE_NAME = "callresponse-birthday-client.json";

    private static volatile int birthdayMonth;
    private static volatile int birthdayDay;
    /** 在线时服务端同步过来的日期，离线时为 null。 */
    private static volatile LocalDate serverDate;
    private static boolean loaded;

    private BirthdayClientData() {
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    /** 首次访问时从配置文件读取上一次同步到的生日。 */
    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            JsonObject root = GsonHelper.parse(Files.readString(path, StandardCharsets.UTF_8));
            int month = GsonHelper.getAsInt(root, "month", 0);
            int day = GsonHelper.getAsInt(root, "day", 0);
            if (BirthdayData.isValid(month, day)) {
                birthdayMonth = month;
                birthdayDay = day;
            }
        } catch (IOException | RuntimeException exception) {
            CallResponseMod.LOGGER.warn("[生日] 读取客户端生日缓存失败", exception);
        }
    }

    private static void save() {
        Path path = file();
        JsonObject root = new JsonObject();
        root.addProperty("month", birthdayMonth);
        root.addProperty("day", birthdayDay);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            CallResponseMod.LOGGER.warn("[生日] 写入客户端生日缓存失败", exception);
        }
    }

    /** 由网络包调用；{@code birthdayMonth <= 0} 表示玩家没设置生日。 */
    public static void update(int birthdayMonth, int birthdayDay,
                              int todayYear, int todayMonth, int todayDay) {
        ensureLoaded();
        boolean changed = birthdayMonth != BirthdayClientData.birthdayMonth
                || birthdayDay != BirthdayClientData.birthdayDay;
        BirthdayClientData.birthdayMonth = birthdayMonth;
        BirthdayClientData.birthdayDay = birthdayDay;
        try {
            serverDate = LocalDate.of(todayYear, todayMonth, todayDay);
        } catch (DateTimeException exception) {
            serverDate = null;
        }
        if (changed) {
            save();
        }
    }

    /** 断开连接：只丢弃服务端日期，生日本身保留给标题界面使用。 */
    public static void onDisconnect() {
        serverDate = null;
    }

    /** 彻底忘记缓存的生日（含磁盘上的文件）。 */
    public static void forget() {
        ensureLoaded();
        birthdayMonth = 0;
        birthdayDay = 0;
        serverDate = null;
        try {
            Files.deleteIfExists(file());
        } catch (IOException exception) {
            CallResponseMod.LOGGER.warn("[生日] 删除客户端生日缓存失败", exception);
        }
    }

    /** 当前客户端玩家是否设置过生日。 */
    public static boolean hasBirthday() {
        ensureLoaded();
        return birthdayMonth > 0 && birthdayDay > 0;
    }

    /** 当前客户端玩家今天是否过生日；未设置生日或还没同步到数据时返回 false。 */
    public static boolean isBirthdayToday() {
        ensureLoaded();
        return birthdayMonth > 0 && birthdayDay > 0
                && BirthdayDates.matches(birthdayMonth, birthdayDay, currentDate());
    }

    /** 判断任意月/日是否落在「今天」上，可用于自定义的生日相关逻辑。 */
    public static boolean isToday(int month, int day) {
        ensureLoaded();
        return BirthdayDates.matches(month, day, currentDate());
    }

    public static int birthdayMonth() {
        ensureLoaded();
        return birthdayMonth;
    }

    public static int birthdayDay() {
        ensureLoaded();
        return birthdayDay;
    }

    /** 判定用的「今天」：在线时是服务端日期，离线时是本机日期。 */
    public static LocalDate currentDate() {
        LocalDate synced = serverDate;
        return synced != null ? synced : LocalDate.now();
    }

    /** 服务端同步过来的日期，未同步（离线）时为 null。 */
    public static LocalDate serverDate() {
        return serverDate;
    }
}
