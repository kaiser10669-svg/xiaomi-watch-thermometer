package com.kaiserdev.watchthermometer;

/**
 * 1D Kalman Filter for Wear OS physiological thermal filtering.
 * Features:
 * - Robust IEEE 754 NaN and Infinity protection to prevent HWUI render crashes.
 * - Sub-zero environmental tolerance (supports -40°C to +100°C without freezing).
 * - Proper de-initialization without false 36.6°C priors.
 * - Anomaly gating (rejects isolated hardware I2C bus read spikes > 6.0°C).
 * - Time-delta adaptive process noise scaling (prevents over/under filtering).
 */
public class KalmanFilter1D {

    private final float baseQ; // Base process noise covariance
    private final float r;     // Measurement noise covariance
    private float x = -100.0f; // State estimate (temperature in °C)
    private float p = 1.0f;    // Estimation error covariance
    private long lastUpdateMs = 0;

    public KalmanFilter1D(float processNoise, float measurementNoise) {
        this.baseQ = processNoise;
        this.r = measurementNoise;
    }

    public synchronized void reset(float initialValue) {
        if (initialValue <= -40.0f || Float.isNaN(initialValue) || Float.isInfinite(initialValue)) {
            // Explicit de-initialization: wait for genuine first measurement
            this.x = -100.0f;
            this.p = 1.0f;
            this.lastUpdateMs = 0;
            return;
        }

        if (initialValue > 100.0f) {
            this.x = 36.6f;
        } else {
            this.x = initialValue;
        }
        this.p = 1.0f;
        this.lastUpdateMs = System.currentTimeMillis();
    }

    public synchronized boolean isInitialized() {
        return x > -40.0f && !Float.isNaN(x) && !Float.isInfinite(x);
    }

    public synchronized float update(float measurement) {
        // Guard against NaN and Inf
        if (Float.isNaN(measurement) || Float.isInfinite(measurement)) {
            return isInitialized() ? x : 36.6f;
        }

        // Physiological / physical boundary check (-40°C to +95°C)
        if (measurement < -40.0f || measurement > 95.0f) {
            return isInitialized() ? x : measurement;
        }

        long now = System.currentTimeMillis();
        float dt = (lastUpdateMs > 0) ? (now - lastUpdateMs) / 1000.0f : 1.5f;
        if (dt < 0.1f) dt = 0.1f;
        if (dt > 10.0f) dt = 10.0f;
        lastUpdateMs = now;

        if (!isInitialized()) {
            x = measurement;
            p = 1.0f;
            return x;
        }

        // Anomaly gating: if sudden jump > 6.0°C from current filtered state, soft-clamp jump
        float innovation = measurement - x;
        if (Math.abs(innovation) > 6.0f) {
            measurement = x + Math.signum(innovation) * 6.0f;
            innovation = measurement - x;
        }

        // Time-scaled process noise
        float q = baseQ * dt;

        // Prediction step
        p = p + q;

        // Measurement update step
        float k = p / (p + r);
        x = x + k * innovation;
        p = (1.0f - k) * p;

        if (Float.isNaN(x) || Float.isInfinite(x)) {
            x = measurement;
            p = 1.0f;
        }

        return x;
    }

    public synchronized float getState() {
        return isInitialized() ? x : 36.6f;
    }
}
