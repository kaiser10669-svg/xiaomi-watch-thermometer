package com.kaiserdev.watchthermometer;

/**
 * 1D Kalman Filter for Wear OS physiological thermal filtering.
 * Separates measurement noise from true physiological drift.
 */
public class KalmanFilter1D {

    private float q; // Process noise covariance (physiological rate of change)
    private float r; // Measurement noise covariance (sensor jitter)
    private float x = -1.0f; // State estimate (temperature in °C)
    private float p = 1.0f;  // Estimation error covariance

    public KalmanFilter1D(float processNoise, float measurementNoise) {
        this.q = processNoise;
        this.r = measurementNoise;
    }

    public void reset(float initialValue) {
        this.x = initialValue;
        this.p = 1.0f;
    }

    public boolean isInitialized() {
        return x > 0;
    }

    public float update(float measurement) {
        if (measurement <= 0) return x;
        if (x <= 0) {
            x = measurement;
            return x;
        }

        // Prediction step
        p = p + q;

        // Measurement update step
        float k = p / (p + r);
        x = x + k * (measurement - x);
        p = (1.0f - k) * p;

        return x;
    }

    public float getState() {
        return x;
    }
}
