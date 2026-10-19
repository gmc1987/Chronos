package com.gmc1987.chronos.mobile.core.common;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Creates the stable remote notification channel and requests Android 13+ consent. */
public final class NotificationSupport {
    public static final String CHANNEL_ID = "chronos_business_updates";

    private NotificationSupport() { }

    public static void initialize(Activity activity) {
        NotificationManager manager = (NotificationManager) activity
                .getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "业务提醒",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("待办、会议、课程与学校通知");
        manager.createNotificationChannel(channel);
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS},
                    2201);
        }
    }
}
