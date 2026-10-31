package com.kaiserdev.watchthermometer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class MedicationAlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "med_reminder_channel";
    public static final int NOTIFICATION_ID = 2026;
    public static final String ACTION_MED_ALARM = "com.kaiserdev.watchthermometer.ACTION_MED_ALARM";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;

        // 1. Wear OS Distinct Medical Alert Vibration (double-pulse + accent pulse)
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = new long[]{0, 250, 150, 250, 200, 450};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Throwable ignored) {}

        // 2. High-priority Heads-Up Notification on Wear OS
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel chan = new NotificationChannel(
                            CHANNEL_ID,
                            "Напоминания о приёме лекарств",
                            NotificationManager.IMPORTANCE_HIGH
                    );
                    chan.setDescription("Оповещение о завершении интервала жаропонижающего");
                    chan.enableVibration(true);
                    chan.setVibrationPattern(new long[]{0, 250, 150, 250, 200, 450});
                    chan.enableLights(true);
                    chan.setLightColor(Color.CYAN);
                    chan.setSound(
                            android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                            new android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                    );
                    nm.createNotificationChannel(chan);
                }

                Intent openIntent = new Intent(context, MainActivity.class);
                openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent contentIntent = PendingIntent.getActivity(context, 0, openIntent, flags);

                Notification.Builder nb;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    nb = new Notification.Builder(context, CHANNEL_ID);
                } else {
                    nb = new Notification.Builder(context);
                }

                nb.setSmallIcon(R.drawable.ic_launcher)
                  .setContentTitle("💊 Пора принять жаропонижающее!")
                  .setContentText("Прошло время интервала. Проверьте температуру тела.")
                  .setContentIntent(contentIntent)
                  .setAutoCancel(true)
                  .setPriority(Notification.PRIORITY_HIGH)
                  .setCategory(Notification.CATEGORY_ALARM)
                  .setOngoing(false);

                nm.notify(NOTIFICATION_ID, nb.build());
            }
        } catch (Throwable ignored) {}
    }
}
