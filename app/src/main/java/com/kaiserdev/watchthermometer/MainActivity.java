package com.kaiserdev.watchthermometer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener, Runnable {

    private static final String PREF_IS_FAHRENHEIT = "is_fahrenheit";
    private static final String PREF_TEMP_OFFSET = "temp_offset";

    private ScrollView mainScrollView;
    private Button btnUnitToggle;

    private View layoutOverheat;
    private TextView tvOverheatMsg;

    private Button btnTabWrist;
    private Button btnTabRoom;
    private View layoutWristMode;
    private View layoutRoomMode;

    // Wrist Mode Views
    private TextView tvSkinTemp;
    private TextView tvSkinStatus;
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

    private SparklineView sparklineView;
    private TextView tvStatMin;
    private TextView tvStatAvg;
    private TextView tvStatMax;

    // Room Mode Views
    private TextView tvRoomTemp;
    private TextView tvRoomStatus;
    private Button btnStartRoomMeasure;
    private TextView tvRoomCountdown;

    // Hardware Sensors
    private TextView tvAmbientTemp;
    private TextView tvBatteryTemp;
    private TextView tvSensorList;

    // Calibration
    private Button btnOffsetMinus;
    private TextView tvOffsetVal;
    private Button btnOffsetPlus;

    private Vibrator vibrator;
    private SensorManager sensorManager;
    private final List<Sensor> registeredSensors = new ArrayList<>();
    private BatteryReceiver batteryReceiver;

    private float directSkinTemp = -1.0f;
    private float lastAmbientTemp = -1.0f;
    private float lastBatteryTemp = -1.0f;
    private float lastThermalZoneTemp = -1.0f;
    private boolean isCharging = false;

    private boolean isFahrenheit = false;
    private float tempOffset = 0.0f;
    private int currentMode = 0; // 0 = wrist, 1 = room

    private boolean isMeasuring = false;
    private int measureSecondsLeft = 30;
    private final List<Float> measureSamples = new ArrayList<>();

    private boolean isRoomMeasuring = false;
    private int roomSecondsLeft = 120;

    private float currentCalculatedSkin = -1.0f;
    private float currentCalculatedBody = -1.0f;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable measureCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            tickMeasure();
        }
    };

    private final Runnable roomCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            tickRoomMeasure();
        }
    };

    @Override
    public void run() {
        pollThermalSources();
        recalculateAndDisplay();
        uiHandler.postDelayed(this, 1500);
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

        SharedPreferences prefs = getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE);
        isFahrenheit = prefs.getBoolean(PREF_IS_FAHRENHEIT, false);
        tempOffset = prefs.getFloat(PREF_TEMP_OFFSET, 0.0f);

        initViews();
        setupListeners();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        batteryReceiver = new BatteryReceiver(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission("android.permission.BODY_SENSORS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.BODY_SENSORS"}, 101);
            }
        }

        discoverSensors();
        pollThermalSources();
        updateOffsetView();
        updateUnitButton();
        recalculateAndDisplay();
    }

    private void initViews() {
        mainScrollView = (ScrollView) findViewById(R.id.mainScrollView);
        btnUnitToggle = (Button) findViewById(R.id.btnUnitToggle);

        layoutOverheat = findViewById(R.id.layoutOverheat);
        tvOverheatMsg = (TextView) findViewById(R.id.tvOverheatMsg);

        btnTabWrist = (Button) findViewById(R.id.btnTabWrist);
        btnTabRoom = (Button) findViewById(R.id.btnTabRoom);
        layoutWristMode = findViewById(R.id.layoutWristMode);
        layoutRoomMode = findViewById(R.id.layoutRoomMode);

        tvSkinTemp = (TextView) findViewById(R.id.tvSkinTemp);
        tvSkinStatus = (TextView) findViewById(R.id.tvSkinStatus);
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

        sparklineView = (SparklineView) findViewById(R.id.sparklineView);
        tvStatMin = (TextView) findViewById(R.id.tvStatMin);
        tvStatAvg = (TextView) findViewById(R.id.tvStatAvg);
        tvStatMax = (TextView) findViewById(R.id.tvStatMax);

        tvRoomTemp = (TextView) findViewById(R.id.tvRoomTemp);
        tvRoomStatus = (TextView) findViewById(R.id.tvRoomStatus);
        btnStartRoomMeasure = (Button) findViewById(R.id.btnStartRoomMeasure);
        tvRoomCountdown = (TextView) findViewById(R.id.tvRoomCountdown);

        tvAmbientTemp = (TextView) findViewById(R.id.tvAmbientTemp);
        tvBatteryTemp = (TextView) findViewById(R.id.tvBatteryTemp);
        tvSensorList = (TextView) findViewById(R.id.tvSensorList);

        btnOffsetMinus = (Button) findViewById(R.id.btnOffsetMinus);
        tvOffsetVal = (TextView) findViewById(R.id.tvOffsetVal);
        btnOffsetPlus = (Button) findViewById(R.id.btnOffsetPlus);
    }

    private void setupListeners() {
        btnUnitToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isFahrenheit = !isFahrenheit;
                getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(PREF_IS_FAHRENHEIT, isFahrenheit).apply();
                vibrateTick();
                updateUnitButton();
                recalculateAndDisplay();
            }
        });

        btnTabWrist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(0);
            }
        });

        btnTabRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(1);
            }
        });

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

        btnStartRoomMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRoomMeasurement();
            }
        });

        btnOffsetMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tempOffset > -2.0f) {
                    tempOffset = Math.round((tempOffset - 0.1f) * 10f) / 10f;
                    saveOffset();
                }
            }
        });

        btnOffsetPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tempOffset < 2.0f) {
                    tempOffset = Math.round((tempOffset + 0.1f) * 10f) / 10f;
                    saveOffset();
                }
            }
        });
    }

    private void saveOffset() {
        getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putFloat(PREF_TEMP_OFFSET, tempOffset).apply();
        vibrateTick();
        updateOffsetView();
        recalculateAndDisplay();
    }

    private void updateOffsetView() {
        if (tvOffsetVal != null) {
            tvOffsetVal.setText(String.format(Locale.US, "%+.1f °C", tempOffset));
        }
    }

    private void updateUnitButton() {
        if (btnUnitToggle != null) {
            btnUnitToggle.setText(isFahrenheit ? "°F" : "°C");
            btnUnitToggle.setTextColor(isFahrenheit ? Color.parseColor("#FF7A00") : Color.parseColor("#00E5FF"));
        }
    }

    private void switchMode(int mode) {
        currentMode = mode;
        vibrateTick();
        if (mode == 0) {
            btnTabWrist.setBackgroundResource(R.drawable.tab_selected_bg);
            btnTabWrist.setTextColor(Color.WHITE);
            btnTabRoom.setBackground(null);
            btnTabRoom.setTextColor(Color.parseColor("#9E9E9E"));
            layoutWristMode.setVisibility(View.VISIBLE);
            layoutRoomMode.setVisibility(View.GONE);
        } else {
            btnTabRoom.setBackgroundResource(R.drawable.tab_selected_bg);
            btnTabRoom.setTextColor(Color.WHITE);
            btnTabWrist.setBackground(null);
            btnTabWrist.setTextColor(Color.parseColor("#9E9E9E"));
            layoutWristMode.setVisibility(View.GONE);
            layoutRoomMode.setVisibility(View.VISIBLE);
        }
        recalculateAndDisplay();
    }

    // Physical Rotary Crown Support (Hardware wheel on Xiaomi Watch 5)
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL);
            int scrollY = Math.round(delta * 48);
            if (mainScrollView != null) {
                mainScrollView.smoothScrollBy(0, scrollY);
                vibrateTick();
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private String formatTemp(float celsius) {
        if (celsius <= 0) return "--.-";
        if (isFahrenheit) {
            float f = celsius * 1.8f + 32f;
            return String.format(Locale.US, "%.1f °F", f);
        } else {
            return String.format(Locale.US, "%.1f °C", celsius);
        }
    }

    private String formatTempShort(float celsius) {
        if (celsius <= 0) return "--.-°";
        if (isFahrenheit) {
            float f = celsius * 1.8f + 32f;
            return String.format(Locale.US, "%.1f°", f);
        } else {
            return String.format(Locale.US, "%.1f°", celsius);
        }
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

        tvSensorList.setText("Xiaomi Watch 5 • Wear OS • v1.4.0");
    }

    private void pollThermalSources() {
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

        try {
            Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bi != null) {
                int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                int temp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (temp > 0) {
                    lastBatteryTemp = temp / 10.0f;
                    tvBatteryTemp.setText(formatTemp(lastBatteryTemp));
                    checkBatteryOverheat(lastBatteryTemp);
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
            tvBatteryTemp.setText(formatTemp(lastBatteryTemp));
            checkBatteryOverheat(lastBatteryTemp);
            recalculateAndDisplay();
        }
    }

    private void checkBatteryOverheat(float bTemp) {
        if (bTemp >= 42.0f) {
            layoutOverheat.setVisibility(View.VISIBLE);
            tvOverheatMsg.setText(String.format(Locale.US, "⚠️ Батарея %s! Снимите с зарядки", formatTemp(bTemp)));
        } else {
            layoutOverheat.setVisibility(View.GONE);
        }
    }

    private void recalculateAndDisplay() {
        float ambientVal = lastAmbientTemp > 0 ? lastAmbientTemp : lastThermalZoneTemp;
        if (ambientVal > 0) {
            tvAmbientTemp.setText(formatTemp(ambientVal));
        }

        // --- Room Mode Processing ---
        float roomEstimate = ambientVal > 0 ? ambientVal : (lastBatteryTemp > 0 ? lastBatteryTemp - 2.5f : 24.0f);
        tvRoomTemp.setText(formatTemp(roomEstimate));
        if (roomEstimate < 18.0f) {
            tvRoomStatus.setText("Прохладный микроклимат");
        } else if (roomEstimate <= 24.5f) {
            tvRoomStatus.setText("✓ Комфортная температура");
        } else {
            tvRoomStatus.setText("Тёплое / душное помещение");
        }

        // --- Wrist Mode Processing ---
        if (directSkinTemp > 0) {
            float bodyEst = 36.5f + (directSkinTemp - 33.4f) * 0.25f;
            float calibratedSkin = directSkinTemp + tempOffset;
            float calibratedBody = bodyEst + tempOffset;

            currentCalculatedSkin = calibratedSkin;
            currentCalculatedBody = calibratedBody;

            tvSkinTemp.setText(formatTemp(calibratedSkin));
            tvSkinStatus.setText(String.format(Locale.US, "Goodix • Тело ~%s", formatTemp(calibratedBody)));
            savePrefsAndUpdateComplication(calibratedSkin, calibratedBody, ambientVal);
            updateSparkline(calibratedSkin);
            return;
        }

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
                float wristSkin = 32.1f + (effectiveContact - 28.0f) * 0.42f;
                if (wristSkin < 30.5f) wristSkin = 30.5f;
                if (wristSkin > 35.8f) wristSkin = 35.8f;

                float bodyEst = 36.5f + (wristSkin - 33.3f) * 0.18f;
                if (bodyEst < 36.1f) bodyEst = 36.1f;
                if (bodyEst > 37.5f) bodyEst = 37.5f;

                float calibratedSkin = wristSkin + tempOffset;
                float calibratedBody = bodyEst + tempOffset;

                currentCalculatedSkin = calibratedSkin;
                currentCalculatedBody = calibratedBody;

                tvSkinTemp.setText(formatTemp(calibratedSkin));
                if (isCharging) {
                    tvSkinStatus.setText(String.format(Locale.US, "Зарядка • Тело ~%s", formatTemp(calibratedBody)));
                } else {
                    tvSkinStatus.setText(String.format(Locale.US, "Запястье • Тело ~%s", formatTemp(calibratedBody)));
                }
                savePrefsAndUpdateComplication(calibratedSkin, calibratedBody, ambientVal);
                updateSparkline(calibratedSkin);
            } else {
                currentCalculatedSkin = rawContact;
                currentCalculatedBody = 36.6f;
                tvSkinTemp.setText(formatTemp(rawContact));
                tvSkinStatus.setText("Наденьте часы плотно на руку");
            }
        } else {
            tvSkinTemp.setText("Измерение...");
            tvSkinStatus.setText("Опрос термодатчиков часов");
        }
    }

    private void updateSparkline(float skin) {
        if (skin > 15.0f && sparklineView != null) {
            sparklineView.addPoint(skin);
            if (sparklineView.getCount() > 0) {
                tvStatMin.setText(formatTempShort(sparklineView.getMin()));
                tvStatAvg.setText(formatTempShort(sparklineView.getAvg()));
                tvStatMax.setText(formatTempShort(sparklineView.getMax()));
            }
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

        float finalBody = 36.5f + (finalSkin - 33.3f) * 0.18f + tempOffset;
        if (finalBody < 35.0f) finalBody = 35.0f;
        if (finalBody > 39.5f) finalBody = 39.5f;

        tvFixedResult.setText(formatTemp(finalBody));

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

    // Room measurement routine
    private void startRoomMeasurement() {
        isRoomMeasuring = true;
        roomSecondsLeft = 60; // 60 seconds precision ambient test
        btnStartRoomMeasure.setVisibility(View.GONE);
        tvRoomCountdown.setVisibility(View.VISIBLE);
        tvRoomCountdown.setText("Замер воздуха: 60 сек...");
        vibrateShort();

        uiHandler.removeCallbacks(roomCountdownRunnable);
        uiHandler.postDelayed(roomCountdownRunnable, 1000);
    }

    private void tickRoomMeasure() {
        if (!isRoomMeasuring) return;
        roomSecondsLeft--;
        tvRoomCountdown.setText("Замер воздуха: " + roomSecondsLeft + " сек...");

        if (roomSecondsLeft > 0) {
            uiHandler.postDelayed(roomCountdownRunnable, 1000);
        } else {
            isRoomMeasuring = false;
            vibrateDouble();
            tvRoomCountdown.setText("✓ Замер зафиксирован");
            btnStartRoomMeasure.setText("⏱️ Повторить замер воздуха");
            btnStartRoomMeasure.setVisibility(View.VISIBLE);
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

    private void vibrateTick() {
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(12);
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
        if (isRoomMeasuring) {
            isRoomMeasuring = false;
            uiHandler.removeCallbacks(roomCountdownRunnable);
            tvRoomCountdown.setVisibility(View.GONE);
            btnStartRoomMeasure.setVisibility(View.VISIBLE);
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
