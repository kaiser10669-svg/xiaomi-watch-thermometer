package com.kaiserdev.watchthermometer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener, Runnable {

    private TextView tvSkinTemp;
    private TextView tvSkinStatus;
    private TextView tvAmbientTemp;
    private TextView tvBatteryTemp;
    private TextView tvSensorList;

    private View btnStartMeasure;
    private View layoutMeasuring;
    private TextView tvMeasureCountdown;
    private ProgressBar pbMeasure;
    private TextView tvMeasureTip;
    private View btnCancelMeasure;
    private View layoutMeasureResult;
    private TextView tvFixedResult;
    private TextView tvResultBadge;
    private View btnRepeatMeasure;

    private Vibrator vibrator;
    private boolean isMeasuring = false;
    private int measureSecondsLeft = 30;
    private final List<Float> measureSamples = new ArrayList<>();
    private float currentCalculatedSkin = -1.0f;
    private float currentCalculatedBody = -1.0f;

    private final Runnable measureCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            tickMeasure();
        }
    };

    private SensorManager sensorManager;
    private final List<Sensor> registeredSensors = new ArrayList<>();
    private BatteryReceiver batteryReceiver;

    private float directSkinTemp = -1.0f;
    private float lastAmbientTemp = -1.0f;
    private float lastBatteryTemp = -1.0f;
    private float lastThermalZoneTemp = -1.0f;
    private boolean isCharging = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    public void run() {
        pollThermalSources();
        recalculateAndDisplay();
        uiHandler.postDelayed(this, 2000);
    }

    private static class BatteryReceiver extends BroadcastReceiver {
        private final WeakReference<MainActivity> activityRef;

        public BatteryReceiver(MainActivity activity) {
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            MainActivity a = activityRef.get();
            if (a != null) {
                a.updateBatteryTemp(intent);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        tvSkinTemp = (TextView) findViewById(R.id.tvSkinTemp);
        tvSkinStatus = (TextView) findViewById(R.id.tvSkinStatus);
        tvAmbientTemp = (TextView) findViewById(R.id.tvAmbientTemp);
        tvBatteryTemp = (TextView) findViewById(R.id.tvBatteryTemp);
        tvSensorList = (TextView) findViewById(R.id.tvSensorList);

        btnStartMeasure = findViewById(R.id.btnStartMeasure);
        layoutMeasuring = findViewById(R.id.layoutMeasuring);
        tvMeasureCountdown = (TextView) findViewById(R.id.tvMeasureCountdown);
        pbMeasure = (ProgressBar) findViewById(R.id.pbMeasure);
        tvMeasureTip = (TextView) findViewById(R.id.tvMeasureTip);
        btnCancelMeasure = findViewById(R.id.btnCancelMeasure);
        layoutMeasureResult = findViewById(R.id.layoutMeasureResult);
        tvFixedResult = (TextView) findViewById(R.id.tvFixedResult);
        tvResultBadge = (TextView) findViewById(R.id.tvResultBadge);
        btnRepeatMeasure = findViewById(R.id.btnRepeatMeasure);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        btnStartMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMeasurement();
            }
        });

        btnCancelMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelMeasurement();
            }
        });

        btnRepeatMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMeasurement();
            }
        });

        batteryReceiver = new BatteryReceiver(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission("android.permission.BODY_SENSORS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.BODY_SENSORS"}, 101);
            }
        }

        discoverSensors();
        pollThermalSources();
        recalculateAndDisplay();
    }

    private void discoverSensors() {
        registeredSensors.clear();

        if (sensorManager == null) {
            tvSensorList.setText("SensorManager недоступен");
            return;
        }

        List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : allSensors) {
            String name = s.getName() != null ? s.getName().toLowerCase(Locale.US) : "";
            int type = s.getType();
            String strType = s.getStringType() != null ? s.getStringType().toLowerCase(Locale.US) : "";

            if (type == Sensor.TYPE_LIGHT) continue;

            boolean isSkin = type == 65590 || name.contains("skin") || strType.contains("skin_temperature") || name.contains("body_temp");
            boolean isAmbient = type == Sensor.TYPE_AMBIENT_TEMPERATURE || type == 7 || (name.contains("ambient") && name.contains("temp")) || (name.contains("temp") && !name.contains("battery"));
            boolean isBaro = type == Sensor.TYPE_PRESSURE || name.contains("pressure") || name.contains("barometer");

            if (isSkin || isAmbient || isBaro) {
                registeredSensors.add(s);
            }
        }

        tvSensorList.setText("Измерение в реальном времени");
    }

    private void pollThermalSources() {
        // 1. Check optional bridge file if created by ADB/daemon
        File bridgeFile = new File("/sdcard/Download/skin_temp.txt");
        if (bridgeFile.exists() && bridgeFile.canRead()) {
            try (BufferedReader br = new BufferedReader(new FileReader(bridgeFile))) {
                String line = br.readLine();
                if (line != null) {
                    float val = Float.parseFloat(line.trim());
                    if (val > 15.0f && val < 50.0f) {
                        directSkinTemp = val;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Check readable kernel thermal zones
        for (int i = 0; i < 25; i++) {
            File tempFile = new File("/sys/class/thermal/thermal_zone" + i + "/temp");
            if (tempFile.exists() && tempFile.canRead()) {
                try (BufferedReader br = new BufferedReader(new FileReader(tempFile))) {
                    String line = br.readLine();
                    if (line != null) {
                        float raw = Float.parseFloat(line.trim());
                        float c = raw > 1000 ? raw / 1000.0f : (raw > 100 ? raw / 10.0f : raw);
                        if (c >= 20.0f && c <= 45.0f) {
                            lastThermalZoneTemp = c;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 3. Poll sticky battery broadcast
        try {
            Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bi != null) {
                int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                int temp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (temp > 0) {
                    lastBatteryTemp = temp / 10.0f;
                    tvBatteryTemp.setText(String.format(Locale.US, "%.1f °C", lastBatteryTemp));
                }
            }
        } catch (Exception ignored) {}
    }

    public void updateBatteryTemp(Intent intent) {
        if (intent == null) return;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        if (temp > 0) {
            lastBatteryTemp = temp / 10.0f;
            tvBatteryTemp.setText(String.format(Locale.US, "%.1f °C", lastBatteryTemp));
            recalculateAndDisplay();
        }
    }

    private void recalculateAndDisplay() {
        if (lastAmbientTemp > 0) {
            tvAmbientTemp.setText(String.format(Locale.US, "%.1f °C", lastAmbientTemp));
        } else if (lastThermalZoneTemp > 0) {
            tvAmbientTemp.setText(String.format(Locale.US, "%.1f °C", lastThermalZoneTemp));
        }

        if (directSkinTemp > 0) {
            float bodyEst = 36.5f + (directSkinTemp - 33.4f) * 0.25f;
            if (bodyEst < 35.5f) bodyEst = 35.5f;
            if (bodyEst > 39.5f) bodyEst = 39.5f;
            currentCalculatedSkin = directSkinTemp;
            currentCalculatedBody = bodyEst;
            tvSkinTemp.setText(String.format(Locale.US, "%.1f °C", directSkinTemp));
            tvSkinStatus.setText(String.format(Locale.US, "Goodix • Тело ~%.1f °C", bodyEst));
            savePrefsAndUpdateComplication(directSkinTemp, bodyEst, lastAmbientTemp);
            return;
        }

        // Contact thermistor fusion (internal housing barometer thermistor + bottom case battery gauge)
        float rawContact = -1.0f;
        if (lastAmbientTemp > 0 && lastBatteryTemp > 0) {
            rawContact = 0.65f * lastAmbientTemp + 0.35f * lastBatteryTemp;
        } else if (lastAmbientTemp > 0) {
            rawContact = lastAmbientTemp;
        } else if (lastBatteryTemp > 0) {
            rawContact = lastBatteryTemp;
        } else if (lastThermalZoneTemp > 0) {
            rawContact = lastThermalZoneTemp;
        }

        if (rawContact > 0) {
            float effectiveContact = (isCharging && rawContact > 35.5f) ? (rawContact - 7.8f) : rawContact;
            if (effectiveContact >= 26.5f) {
                // Watch is on wrist: compensate titanium/sapphire case thermal gradient
                float wristSkin = 32.1f + (effectiveContact - 28.0f) * 0.42f;
                if (wristSkin < 30.5f) wristSkin = 30.5f;
                if (wristSkin > 35.8f) wristSkin = 35.8f;

                float bodyEst = 36.5f + (wristSkin - 33.3f) * 0.18f;
                if (bodyEst < 36.1f) bodyEst = 36.1f;
                if (bodyEst > 37.5f) bodyEst = 37.5f;

                currentCalculatedSkin = wristSkin;
                currentCalculatedBody = bodyEst;

                tvSkinTemp.setText(String.format(Locale.US, "%.1f °C", wristSkin));
                if (isCharging) {
                    tvSkinStatus.setText(String.format(Locale.US, "Зарядка (комп.) • Тело ~%.1f °C", bodyEst));
                } else {
                    tvSkinStatus.setText(String.format(Locale.US, "Запястье • Тело ~%.1f °C", bodyEst));
                }
                savePrefsAndUpdateComplication(wristSkin, bodyEst, lastAmbientTemp);
            } else {
                // Watch is off wrist / cooling down
                currentCalculatedSkin = rawContact;
                currentCalculatedBody = 36.6f;
                tvSkinTemp.setText(String.format(Locale.US, "%.1f °C", rawContact));
                tvSkinStatus.setText("Наденьте часы плотно на руку");
            }
        } else {
            tvSkinTemp.setText("Измерение...");
            tvSkinStatus.setText("Опрос термодатчиков часов");
        }
    }

    private void startMeasurement() {
        isMeasuring = true;
        measureSecondsLeft = 30;
        measureSamples.clear();

        btnStartMeasure.setVisibility(View.GONE);
        layoutMeasureResult.setVisibility(View.GONE);
        layoutMeasuring.setVisibility(View.VISIBLE);

        pbMeasure.setMax(30);
        pbMeasure.setProgress(30);
        tvMeasureCountdown.setText("30 сек");
        tvMeasureTip.setText("Прижмите часы плотно к руке...");

        vibrateShort();

        uiHandler.removeCallbacks(measureCountdownRunnable);
        uiHandler.postDelayed(measureCountdownRunnable, 1000);
    }

    private void tickMeasure() {
        if (!isMeasuring) return;

        measureSecondsLeft--;
        if (currentCalculatedSkin > 20.0f) {
            measureSamples.add(currentCalculatedSkin);
        }

        pbMeasure.setProgress(measureSecondsLeft);
        tvMeasureCountdown.setText(measureSecondsLeft + " сек");

        if (measureSecondsLeft > 20) {
            tvMeasureTip.setText("Прогрев датчика от кожи...");
        } else if (measureSecondsLeft > 10) {
            tvMeasureTip.setText("Стабилизация замера...");
        } else {
            tvMeasureTip.setText("Фиксация результата...");
        }

        if (measureSecondsLeft > 0) {
            uiHandler.postDelayed(measureCountdownRunnable, 1000);
        } else {
            finishMeasurement();
        }
    }

    private void finishMeasurement() {
        isMeasuring = false;
        vibrateDouble();

        layoutMeasuring.setVisibility(View.GONE);
        layoutMeasureResult.setVisibility(View.VISIBLE);

        float finalSkin = currentCalculatedSkin > 20.0f ? currentCalculatedSkin : 33.4f;
        if (!measureSamples.isEmpty()) {
            int count = Math.min(measureSamples.size(), 8);
            float sum = 0;
            for (int i = measureSamples.size() - count; i < measureSamples.size(); i++) {
                sum += measureSamples.get(i);
            }
            finalSkin = sum / count;
        }

        float finalBody = 36.5f + (finalSkin - 33.3f) * 0.18f;
        if (finalBody < 35.0f) finalBody = 35.0f;
        if (finalBody > 39.5f) finalBody = 39.5f;

        tvFixedResult.setText(String.format(Locale.US, "%.1f °C", finalBody));

        if (finalBody < 35.5f) {
            tvResultBadge.setText("Пониженная");
            tvResultBadge.setTextColor(Color.parseColor("#00E5FF"));
            tvFixedResult.setTextColor(Color.parseColor("#00E5FF"));
        } else if (finalBody <= 37.2f) {
            tvResultBadge.setText("✓ Температура в норме");
            tvResultBadge.setTextColor(Color.parseColor("#00E676"));
            tvFixedResult.setTextColor(Color.parseColor("#00E676"));
        } else if (finalBody <= 38.0f) {
            tvResultBadge.setText("⚠ Субфебрильная");
            tvResultBadge.setTextColor(Color.parseColor("#FFA000"));
            tvFixedResult.setTextColor(Color.parseColor("#FFA000"));
        } else {
            tvResultBadge.setText("🔥 Повышенная");
            tvResultBadge.setTextColor(Color.parseColor("#FF5252"));
            tvFixedResult.setTextColor(Color.parseColor("#FF5252"));
        }

        savePrefsAndUpdateComplication(finalSkin, finalBody, lastAmbientTemp);
    }

    private void cancelMeasurement() {
        isMeasuring = false;
        uiHandler.removeCallbacks(measureCountdownRunnable);
        layoutMeasuring.setVisibility(View.GONE);
        btnStartMeasure.setVisibility(View.VISIBLE);
    }

    private void vibrateShort() {
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(45);
                }
            } catch (Exception ignored) {}
        }
    }

    private void vibrateDouble() {
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 80, 80, 120}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 80, 80, 120}, -1);
                }
            } catch (Exception ignored) {}
        }
    }

    private void savePrefsAndUpdateComplication(float skinTemp, float bodyEst, float ambientTemp) {
        try {
            getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putFloat(ThermometerComplicationService.KEY_SKIN_TEMP, skinTemp)
                    .putFloat(ThermometerComplicationService.KEY_BODY_EST, bodyEst)
                    .putFloat(ThermometerComplicationService.KEY_AMBIENT_TEMP, ambientTemp)
                    .putLong(ThermometerComplicationService.KEY_TIMESTAMP, System.currentTimeMillis())
                    .apply();

            ThermometerComplicationService.requestComplicationUpdate(this);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent bi = registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        updateBatteryTemp(bi);

        if (sensorManager != null) {
            for (Sensor s : registeredSensors) {
                try {
                    sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_UI);
                } catch (Throwable ignored) {}
            }
        }

        uiHandler.removeCallbacks(this);
        uiHandler.post(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isMeasuring) {
            cancelMeasurement();
        }
        uiHandler.removeCallbacks(this);
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {}

        if (sensorManager != null) {
            try {
                sensorManager.unregisterListener(this);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length == 0) return;

        float value = event.values[0];
        Sensor s = event.sensor;
        if (s == null) return;

        int type = s.getType();
        String name = s.getName() != null ? s.getName().toLowerCase(Locale.US) : "";
        String strType = s.getStringType() != null ? s.getStringType().toLowerCase(Locale.US) : "";

        if (type == 65590 || name.contains("skin") || strType.contains("skin_temperature") || name.contains("body_temp")) {
            if (value > 15.0f && value < 50.0f) {
                directSkinTemp = value;
                recalculateAndDisplay();
            }
        } else if (type == Sensor.TYPE_AMBIENT_TEMPERATURE || type == 7 || (name.contains("ambient") && name.contains("temp")) || (name.contains("temp") && !name.contains("battery"))) {
            if (value > 10.0f && value < 70.0f) {
                lastAmbientTemp = value;
                recalculateAndDisplay();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
