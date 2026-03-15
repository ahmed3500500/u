package com.example.telegramcallnotifier;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class PingNotifier {

    private static final String CHANNEL_ID = "ping_alive_channel";
    private static final int NOTIFICATION_ID = 4001;

    public static void showPingNotification(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ping Alive Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Ping success notifications");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_notify_sync)
                        .setContentTitle("Device Alive")
                        .setContentText("Ping OK - server responded")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setAutoCancel(true);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    public static void updateForegroundNotification(Context context, String title, String text) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Ping Alive Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        Notification notification =
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_notify_sync)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setOngoing(false)
                        .build();

        manager.notify(NOTIFICATION_ID, notification);
    }

    public static void launchWakeActivity(Context context) {
        Intent wakeIntent = new Intent(context, WakeActivity.class);
        wakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(wakeIntent);
    }
}
