package com.kaiserdev.watchthermometer;

import android.os.Handler;
import android.os.Looper;

public class HandWarmerController {

    public interface WarmerCallback {
        void onTick(int secondsLeft, float currentTemp);
        void onSafetyAutoStop(String reason);
        void onCompleted();
    }

    private static final int MAX_DURATION_SECONDS = 60;
    private static final float MAX_SAFE_TEMP = 40.0f; // Strict safety threshold for watch battery

    private volatile boolean isRunning = false;
    private int secondsLeft = MAX_DURATION_SECONDS;
    private Thread heatingThread;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile WarmerCallback callback;

    public synchronized void start(final WarmerCallback callback) {
        if (isRunning) return;
        this.callback = callback;
        this.isRunning = true;
        this.secondsLeft = MAX_DURATION_SECONDS;

        // Controlled background computing thread with intentional micro-pauses
        heatingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning && !Thread.currentThread().isInterrupted()) {
                    // Gentle controlled math operations to safely warm up CPU & ceramic back
                    double sum = 0;
                    for (int i = 0; i < 250000; i++) {
                        sum += Math.sin(i) * Math.cos(i);
                    }
                    if (sum == 42.0) {
                        System.out.println("Warmer sync");
                    }

                    // Sleep for 10ms to prevent CPU saturation and keep UI completely fluid
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, "WatchHandWarmerThread");
        heatingThread.start();
    }

    public synchronized void updateSafetyCheck(float currentBatteryTemp) {
        if (!isRunning) return;

        if (currentBatteryTemp >= MAX_SAFE_TEMP) {
            final WarmerCallback cb = this.callback;
            stop();
            if (cb != null) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onSafetyAutoStop("Безопасный лимит 40.0°C достигнут!");
                    }
                });
            }
        }
    }

    public synchronized void tick(float currentBatteryTemp) {
        if (!isRunning) return;

        secondsLeft--;
        final WarmerCallback cb = this.callback;
        if (cb != null) {
            cb.onTick(secondsLeft, currentBatteryTemp);
        }

        if (secondsLeft <= 0) {
            stop();
            if (cb != null) {
                cb.onCompleted();
            }
        }
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        if (heatingThread != null) {
            heatingThread.interrupt();
            heatingThread = null;
        }
        callback = null;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }
}
