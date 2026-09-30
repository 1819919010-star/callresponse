package com.github.JumDa5he.callresponse.compat.birthday;

import java.time.LocalDate;
import java.time.Year;

/**
 * 生日日期判定的公共逻辑，服务端庆祝与客户端判断共用同一套规则，
 * 避免两边对「今天是不是生日」得出不同结论。
 */
public final class BirthdayDates {
    private BirthdayDates() {
    }

    /**
     * 判断给定月/日是否落在 {@code date} 这一天。
     *
     * <p>2 月 29 日出生的玩家在非闰年顺延到 3 月 1 日，与服务端的庆祝规则一致。</p>
     */
    public static boolean matches(int month, int day, LocalDate date) {
        if (date == null || month < 1 || day < 1) {
            return false;
        }
        if (month == 2 && day == 29) {
            if (date.getMonthValue() == 2 && date.getDayOfMonth() == 29) {
                return true;
            }
            return !Year.isLeap(date.getYear())
                    && date.getMonthValue() == 3 && date.getDayOfMonth() == 1;
        }
        return month == date.getMonthValue() && day == date.getDayOfMonth();
    }
}
