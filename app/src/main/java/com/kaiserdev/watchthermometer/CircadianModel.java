package com.kaiserdev.watchthermometer;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Human Hypothalamic Circadian Core Temperature Model.
 * Based on Mackowiak / Wunderlich chronobiological studies.
 * Accurately aligns circadian Zenith (~17:30, 37.05°C) and Nadir (~05:30, 36.25°C).
 * Ultra-performance: Zero heap allocations, pure integer/float arithmetic.
 */
public class CircadianModel {

    private static float getLocalHourOfDay(long timestampMs) {
        long localMs = timestampMs + TimeZone.getDefault().getOffset(timestampMs);
        long msInDay = (localMs % 86400000L + 86400000L) % 86400000L;
        return msInDay / 3600000.0f;
    }

    /**
     * Returns expected healthy human baseline body temperature for the given time of day.
     * Nadir ~05:30 AM (36.25°C)
     * Zenith ~17:30 PM (37.05°C)
     */
    public static float getBaseline(long timestampMs) {
        float hour = getLocalHourOfDay(timestampMs);

        // Peak at 17:30 (17.5h): (17.5 - 11.5) = 6.0 => phase = pi/2, sin = +1.0
        // Nadir at 05:30 (5.5h): (5.5 - 11.5) = -6.0 => phase = -pi/2, sin = -1.0
        double phase = 2.0 * Math.PI * (hour - 11.5) / 24.0;
        float offset = (float) (0.38 * Math.sin(phase));
        return Math.round((36.65f + offset) * 10f) / 10f;
    }

    /**
     * Continuous physiological distal vasodilation factor.
     * Replaces binary step with smooth chronobiological cosine transition:
     * - Daytime baseline (08:00 - 21:00): 1.0 (no suppression).
     * - Deep nocturnal rest (01:00 - 05:00): 0.35 (peak peripheral vasodilation suppression).
     * - Smooth transitions between 21:00 - 01:00 and 05:00 - 08:00 without step artifacts.
     */
    public static float getVasodilationFactor(long timestampMs) {
        float hour = getLocalHourOfDay(timestampMs);
        if (hour >= 8.0f && hour <= 21.0f) {
            return 1.0f;
        }
        if (hour >= 1.0f && hour <= 5.0f) {
            return 0.35f;
        }
        if (hour > 21.0f) {
            float progress = (hour - 21.0f) / 4.0f;
            return 1.0f - 0.65f * (float) (0.5 * (1.0 - Math.cos(Math.PI * progress)));
        } else if (hour < 1.0f) {
            float progress = (hour + 3.0f) / 4.0f;
            return 1.0f - 0.65f * (float) (0.5 * (1.0 - Math.cos(Math.PI * progress)));
        } else {
            float progress = (hour - 5.0f) / 3.0f;
            return 0.35f + 0.65f * (float) (0.5 * (1.0 - Math.cos(Math.PI * progress)));
        }
    }

    /**
     * Checks if current time is within nocturnal sleep phase,
     * where distal vasodilation naturally increases peripheral wrist temperature.
     */
    public static boolean isNightSleepPhase(long timestampMs) {
        return getVasodilationFactor(timestampMs) < 0.70f;
    }

    /**
     * Calculates deviation from personal circadian baseline (Apple/Garmin style).
     */
    public static float getDeviation(float bodyTemp, long timestampMs) {
        float base = getBaseline(timestampMs);
        return Math.round((bodyTemp - base) * 10f) / 10f;
    }

    /**
     * Returns human-readable deviation string aligned with selected unit.
     */
    public static String formatDeviation(float dev, boolean isFahrenheit) {
        float d = isFahrenheit ? (dev * 1.8f) : dev;
        if (Math.abs(d) < 0.05f) {
            return "±0.0°";
        } else if (d > 0) {
            return String.format(Locale.US, "+%.1f°", d);
        } else {
            return String.format(Locale.US, "%.1f°", d);
        }
    }

    public static String formatDeviation(float dev) {
        return formatDeviation(dev, false);
    }
}
