package com.gmc1987.chronos.mobile.core.common;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class DateFormats {
    private DateFormats() {}

    public static String iso(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(date);
    }

    public static String display(Date date) {
        return new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(date);
    }

    public static Calendar weekStart(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int day = calendar.get(Calendar.DAY_OF_WEEK);
        calendar.add(Calendar.DATE, day == Calendar.SUNDAY ? -6 : Calendar.MONDAY - day);
        return calendar;
    }

    public static Calendar weekEnd(Calendar start) {
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DATE, 6);
        return end;
    }
}
