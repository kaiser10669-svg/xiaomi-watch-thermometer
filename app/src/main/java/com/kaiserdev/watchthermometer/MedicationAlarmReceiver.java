package com.kaiserdev.watchthermometer;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class MedicationAlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "med_reminder_channel";
    public static final int NOTIFICATION_ID = 2026;
    public static final String ACTION_MED_ALARM = "com.kaiserdev.watchthermometer.ACTION_MED_ALARM";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;

        if (intent != null) {
            String action = intent.getAction();
            if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
                restorePendingAlarm(context);
                return;
            }
        }

        triggerAlarmNotification(context);
    }

    private void restorePendingAlarm(Context context) {
        try {
            SharedPreferences sp = context.getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE);
            long target = sp.getLong("med_timer_target", 0);
            if (target <= 0) return;

            long diff = target - System.currentTimeMillis();
            if (diff > 0) {
                AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                if (am != null) {
                    Intent alarmIntent = new Intent(context, MedicationAlarmReceiver.class);
                    alarmIntent.setAction(ACTION_MED_ALARM);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                    if (Build.VERSION.SDK_INT >= 31) {
                        flags |= PendingIntent.FLAG_IMMUTABLE;
                    }
                    PendingIntent pi = PendingIntent.getBroadcast(context, 1001, alarmIntent, flags);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (am.canScheduleExactAlarms()) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi);
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi);
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target, pi);
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, target, pi);
                    }
                }
            } else if (diff > -7200000L) {
                // Expired during reboot window, fire notification immediately
                triggerAlarmNotification(context);
            } else {
                sp.edit().remove("med_timer_target").apply();
            }
        } catch (Throwable ignored) {}
    }

    private void triggerAlarmNotification(Context context) {
        // Acquire a temporary 3.5s WakeLock to guarantee vibration pattern completes during Doze/deep sleep
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "watchthermometer:MedAlarmWl");
                wakeLock.acquire(3500);
            }
        } catch (Throwable ignored) {}

        // 1. Wear OS Distinct Medical Alert Vibration (breaks through Do Not Disturb)
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                long[] pattern = new long[]{0, 250, 150, 250, 200, 450};
                if (Build.VERSION.SDK_INT >= 33) {
                    android.os.VibrationAttributes va = new android.os.VibrationAttributes.Builder()
                            .setUsage(android.os.VibrationAttributes.USAGE_ALARM)
                            .build();
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), va);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    android.media.AudioAttributes aa = new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build();
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), aa);
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
                    chan.setBypassDnd(true);
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

                // Use monochrome complication icon for Wear OS notification spec compliance
                nb.setSmallIcon(R.drawable.ic_complication_temp)
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
