package com.kaiserdev.watchthermometer;

import java.util.Calendar;
import java.util.Locale;

/**
 * Human Hypothalamic Circadian Core Temperature Model.
 * Based on Mackowiak / Wunderlich chronobiological studies.
 * Accurately aligns circadian Zenith (~17:30, 37.05°C) and Nadir (~05:30, 36.25°C).
 */
public class CircadianModel {

    /**
     * Returns expected healthy human baseline body temperature for the given time of day.
     * Nadir ~05:30 AM (36.25°C)
     * Zenith ~17:30 PM (37.05°C)
     */
    public static float getBaseline(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        float hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0f;

        // Peak at 17:30 (17.5h): (17.5 - 11.5) = 6.0 => phase = pi/2, sin = +1.0
        // Nadir at 05:30 (5.5h): (5.5 - 11.5) = -6.0 => phase = -pi/2, sin = -1.0
        double phase = 2.0 * Math.PI * (hour - 11.5) / 24.0;
        float offset = (float) (0.38 * Math.sin(phase));
        return Math.round((36.65f + offset) * 10f) / 10f;
    }

    /**
     * Checks if current time is within nocturnal sleep phase (23:00 - 06:00),
     * where distal vasodilation naturally increases peripheral wrist temperature.
     */
    public static boolean isNightSleepPhase(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        return hour >= 23 || hour < 6;
    }

    /**
     * Calculates deviation from personal circadian baseline (Apple/Garmin style).
     */
    public static float getDeviation(float bodyTemp, long timestampMs) {
        float base = getBaseline(timestampMs);
        return Math.round((bodyTemp - base) * 10f) / 10f;
    }

    /**
     * Returns human-readable deviation string.
     */
    public static String formatDeviation(float dev) {
        if (Math.abs(dev) < 0.05f) {
            return "±0.0°";
        } else if (dev > 0) {
            return String.format(Locale.US, "+%.1f°", dev);
        } else {
            return String.format(Locale.US, "%.1f°", dev);
        }
    }
}
