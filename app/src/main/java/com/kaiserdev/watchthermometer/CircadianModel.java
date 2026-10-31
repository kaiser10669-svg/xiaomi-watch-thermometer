package com.kaiserdev.watchthermometer;

import java.util.Calendar;
import java.util.Locale;

/**
 * Human Hypothalamic Circadian Core Temperature Model.
 * Based on Mackowiak / Wunderlich chronobiological studies.
 */
public class CircadianModel {

    /**
     * Returns expected healthy human baseline body temperature for the given time of day.
     * Nadir ~04:00 AM (36.25°C)
     * Zenith ~17:30 PM (37.05°C)
     */
    public static float getBaseline(long timestampMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestampMs);
        float hour = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60.0f;

        // 24-hour harmonic phase shifted such that minimum is at ~04:00 and maximum at ~16:00-17:30
        double phase = 2.0 * Math.PI * (hour - 9.0) / 24.0;
        float offset = (float) (0.38 * Math.sin(phase));
        return Math.round((36.65f + offset) * 10f) / 10f;
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
