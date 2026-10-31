package com.kaiserdev.watchthermometer;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements SensorEventListener, Runnable {

    private static final String PREF_IS_FAHRENHEIT = "is_fahrenheit";
    private static final String PREF_TEMP_OFFSET = "temp_offset";
    private static final String PREF_MED_TIMER_TARGET = "med_timer_target";

    private ScrollView mainScrollView;
    private ImageButton btnVoiceMute;
    private Button btnUnitToggle;

    private View layoutOverheat;
    private TextView tvOverheatMsg;

    // 4 Modes: 0=wrist, 1=temple, 2=water, 3=room
    private Button btnTabWrist;
    private Button btnTabTemple;
    private Button btnTabWater;
    private Button btnTabRoom;

    private View layoutWristMode;
    private View layoutTempleMode;
    private View layoutWaterMode;
    private View layoutRoomMode;

    // Wrist Mode Views
    private TextView tvHeaderWrist;
    private TextView tvSkinTemp;
    private TextView tvSkinStatus;
    private TextView tvSensorDetail;
    private MercuryThermometerView mercuryViewWrist;
    private View btnStartMeasure;
    private View layoutMeasuring;
    private TextView tvMeasureCountdown;
    private ProgressBar pbMeasure;
    private TextView tvMeasureTip;
    private View btnCancelMeasure;
    private View layoutMeasureResult;
    private TextView tvFixedResult;
    private ImageButton btnSpeakResult;
    private TextView tvResultBadge;
    private View btnRepeatMeasure;

    private SparklineView sparklineView;
    private TextView tvStatMin;
    private TextView tvStatAvg;
    private TextView tvStatMax;

    // Thermal Balance Views
    private TextView tvDeltaT;
    private TextView tvThermalStatus;
    private TextView tvThermogenesisCalories;
    private TextView tvThermalDesc;

    // Temple Mode Views
    private TextView tvHeaderTemple;
    private TextView tvTempleTemp;
    private TextView tvTempleStatus;
    private MercuryThermometerView mercuryViewTemple;
    private Button btnStartTempleMeasure;
    private TextView tvTempleCountdown;
    private View layoutTempleResult;
    private TextView tvTempleFixedResult;
    private ImageButton btnSpeakTempleResult;
    private TextView tvTempleResultBadge;

    private View layoutRoomResult;
    private TextView tvRoomFixedResult;
    private TextView tvRoomResultBadge;
    private ImageButton btnSpeakRoomResult;

    private float lastMeasuredRoom = -1.0f;
    private volatile boolean isPlugged = false;
    private long lastHeartRateTimestampMs = 0;

    // Water Mode Views
    private TextView tvWaterTemp;
    private TextView tvWaterBadge;
    private TextView tvWaterStatus;
    private Button btnStartWaterMeasure;
    private TextView tvWaterCountdown;
    private View layoutWaterResult;
    private TextView tvWaterFixedResult;
    private ImageButton btnSpeakWaterResult;

    // Room Mode Views
    private TextView tvRoomTemp;
    private TextView tvRoomStatus;
    private Button btnStartRoomMeasure;
    private TextView tvRoomCountdown;

    // Fever Advisor & Med Timer Views
    private TextView tvAdvisorStatus;
    private TextView tvAdvisorAdvice;
    private TextView tvMedTimerCountdown;
    private Button btnTimer4h;
    private Button btnTimer6h;
    private Button btnTimerReset;

    // History Log Views
    private LinearLayout layoutHistoryList;
    private TextView tvHistoryEmpty;
    private Button btnClearHistory;
    private Button btnExportReport;

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
    private VoiceAssistantManager voiceAssistant;

    private volatile float directSkinTemp = -1.0f;
    private volatile float hardwareSkinTemp = -1.0f;
    private volatile float hardwareCpuTemp = -1.0f;
    private volatile float hardwareBattGaugeTemp = -1.0f;
    private volatile boolean isOnWrist = true;
    private volatile float lastAmbientTemp = -1.0f;
    private volatile float lastBatteryTemp = -1.0f;
    private volatile float lastThermalZoneTemp = -1.0f;
    private volatile boolean isCharging = false;

    private int cachedSkinZone = -1;
    private int cachedCpuZone = -1;
    private int cachedBattZone = -1;
    private boolean thermalZonesDiscovered = false;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private long lastCrownVibeMs = 0;
    private long lastComplicationBroadcastMs = 0;
    private float lastBroadcastBody = -100.0f;

    private boolean isFahrenheit = false;
    private float tempOffset = 0.0f;
    private int currentMode = 0; // 0=wrist, 1=temple, 2=water, 3=room

    // Measurement States
    private boolean isMeasuring = false;
    private int measureSecondsLeft = 30;
    private final List<Float> measureSamples = new ArrayList<>();
    private float lastMeasuredBodyWrist = 36.6f;

    private boolean isTempleMeasuring = false;
    private int templeSecondsLeft = 15;
    private final List<Float> templeSamples = new ArrayList<>();
    private float lastMeasuredBodyTemple = 36.6f;

    private boolean isWaterMeasuring = false;
    private int waterSecondsLeft = 10;
    private final List<Float> waterSamples = new ArrayList<>();
    private float lastMeasuredWater = 37.0f;

    private boolean isRoomMeasuring = false;
    private int roomSecondsLeft = 60;

    private float currentCalculatedSkin = -1.0f;
    private float currentCalculatedBody = -1.0f;

    // v3.4.0 Apex Clinical Systems
    private final KalmanFilter1D kalmanSkin = new KalmanFilter1D(0.008f, 0.12f);
    private final KalmanFilter1D kalmanBody = new KalmanFilter1D(0.005f, 0.08f);
    private volatile float lastHeartRateBpm = -1.0f;
    private float displayedBodyTemp = -1.0f;
    private ValueAnimator tempAnimator;
    private AlertDialog clinicalDialog;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable measureCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            tickMeasure();
        }
    };

    private final Runnable templeCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            tickTempleMeasure();
        }
    };

    private final Runnable waterCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            tickWaterMeasure();
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
        updateMedTimerView();

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
        setContentView(R.layout.activity_main);

        voiceAssistant = new VoiceAssistantManager(this);

        SharedPreferences prefs = getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE);
        isFahrenheit = prefs.getBoolean(PREF_IS_FAHRENHEIT, false);
        tempOffset = prefs.getFloat(PREF_TEMP_OFFSET, 0.0f);

        initViews();
        setupListeners();

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        batteryReceiver = new BatteryReceiver(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            List<String> perms = new ArrayList<>();
            if (checkSelfPermission("android.permission.BODY_SENSORS") != PackageManager.PERMISSION_GRANTED) {
                perms.add("android.permission.BODY_SENSORS");
            }
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                perms.add("android.permission.POST_NOTIFICATIONS");
            }
            if (!perms.isEmpty()) {
                requestPermissions(perms.toArray(new String[0]), 101);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission("android.permission.BODY_SENSORS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.BODY_SENSORS"}, 101);
            }
        }

        discoverSensors();
        pollThermalSources();
        updateOffsetView();
        updateUnitButton();
        updateVoiceButton();
        updateHistoryView();
        updateMedTimerView();
        recalculateAndDisplay();
    }

    private void setWakeLockActive(boolean active) {
        if (active) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            discoverSensors();
        }
    }

    private void initViews() {
        mainScrollView = (ScrollView) findViewById(R.id.mainScrollView);
        btnVoiceMute = (ImageButton) findViewById(R.id.btnVoiceMute);
        btnUnitToggle = (Button) findViewById(R.id.btnUnitToggle);

        layoutOverheat = findViewById(R.id.layoutOverheat);
        tvOverheatMsg = (TextView) findViewById(R.id.tvOverheatMsg);

        btnTabWrist = (Button) findViewById(R.id.btnTabWrist);
        btnTabTemple = (Button) findViewById(R.id.btnTabTemple);
        btnTabWater = (Button) findViewById(R.id.btnTabWater);
        btnTabRoom = (Button) findViewById(R.id.btnTabRoom);

        layoutWristMode = findViewById(R.id.layoutWristMode);
        layoutTempleMode = findViewById(R.id.layoutTempleMode);
        layoutWaterMode = findViewById(R.id.layoutWaterMode);
        layoutRoomMode = findViewById(R.id.layoutRoomMode);

        // Wrist
        tvHeaderWrist = (TextView) findViewById(R.id.tvHeaderWrist);
        tvSkinTemp = (TextView) findViewById(R.id.tvSkinTemp);
        tvSkinStatus = (TextView) findViewById(R.id.tvSkinStatus);
        tvSensorDetail = (TextView) findViewById(R.id.tvSensorDetail);
        mercuryViewWrist = (MercuryThermometerView) findViewById(R.id.mercuryViewWrist);
        btnStartMeasure = findViewById(R.id.btnStartMeasure);
        layoutMeasuring = findViewById(R.id.layoutMeasuring);
        tvMeasureCountdown = (TextView) findViewById(R.id.tvMeasureCountdown);
        pbMeasure = (ProgressBar) findViewById(R.id.pbMeasure);
        tvMeasureTip = (TextView) findViewById(R.id.tvMeasureTip);
        btnCancelMeasure = findViewById(R.id.btnCancelMeasure);
        layoutMeasureResult = findViewById(R.id.layoutMeasureResult);
        tvFixedResult = (TextView) findViewById(R.id.tvFixedResult);
        btnSpeakResult = (ImageButton) findViewById(R.id.btnSpeakResult);
        tvResultBadge = (TextView) findViewById(R.id.tvResultBadge);
        btnRepeatMeasure = findViewById(R.id.btnRepeatMeasure);

        sparklineView = (SparklineView) findViewById(R.id.sparklineView);
        tvStatMin = (TextView) findViewById(R.id.tvStatMin);
        tvStatAvg = (TextView) findViewById(R.id.tvStatAvg);
        tvStatMax = (TextView) findViewById(R.id.tvStatMax);

        // Thermal Balance
        tvDeltaT = (TextView) findViewById(R.id.tvDeltaT);
        tvThermalStatus = (TextView) findViewById(R.id.tvThermalStatus);
        tvThermogenesisCalories = (TextView) findViewById(R.id.tvThermogenesisCalories);
        tvThermalDesc = (TextView) findViewById(R.id.tvThermalDesc);

        // Temple
        tvHeaderTemple = (TextView) findViewById(R.id.tvHeaderTemple);
        tvTempleTemp = (TextView) findViewById(R.id.tvTempleTemp);
        tvTempleStatus = (TextView) findViewById(R.id.tvTempleStatus);
        mercuryViewTemple = (MercuryThermometerView) findViewById(R.id.mercuryViewTemple);
        btnStartTempleMeasure = (Button) findViewById(R.id.btnStartTempleMeasure);
        tvTempleCountdown = (TextView) findViewById(R.id.tvTempleCountdown);
        layoutTempleResult = findViewById(R.id.layoutTempleResult);
        tvTempleFixedResult = (TextView) findViewById(R.id.tvTempleFixedResult);
        btnSpeakTempleResult = (ImageButton) findViewById(R.id.btnSpeakTempleResult);
        tvTempleResultBadge = (TextView) findViewById(R.id.tvTempleResultBadge);

        layoutRoomResult = findViewById(R.id.layoutRoomResult);
        tvRoomFixedResult = (TextView) findViewById(R.id.tvRoomFixedResult);
        tvRoomResultBadge = (TextView) findViewById(R.id.tvRoomResultBadge);
        btnSpeakRoomResult = (ImageButton) findViewById(R.id.btnSpeakRoomResult);

        // Water
        tvWaterTemp = (TextView) findViewById(R.id.tvWaterTemp);
        tvWaterBadge = (TextView) findViewById(R.id.tvWaterBadge);
        tvWaterStatus = (TextView) findViewById(R.id.tvWaterStatus);
        btnStartWaterMeasure = (Button) findViewById(R.id.btnStartWaterMeasure);
        tvWaterCountdown = (TextView) findViewById(R.id.tvWaterCountdown);
        layoutWaterResult = findViewById(R.id.layoutWaterResult);
        tvWaterFixedResult = (TextView) findViewById(R.id.tvWaterFixedResult);
        btnSpeakWaterResult = (ImageButton) findViewById(R.id.btnSpeakWaterResult);

        // Room
        tvRoomTemp = (TextView) findViewById(R.id.tvRoomTemp);
        tvRoomStatus = (TextView) findViewById(R.id.tvRoomStatus);
        btnStartRoomMeasure = (Button) findViewById(R.id.btnStartRoomMeasure);
        tvRoomCountdown = (TextView) findViewById(R.id.tvRoomCountdown);

        // Fever Advisor & Med Timer
        tvAdvisorStatus = (TextView) findViewById(R.id.tvAdvisorStatus);
        tvAdvisorAdvice = (TextView) findViewById(R.id.tvAdvisorAdvice);
        tvMedTimerCountdown = (TextView) findViewById(R.id.tvMedTimerCountdown);
        btnTimer4h = (Button) findViewById(R.id.btnTimer4h);
        btnTimer6h = (Button) findViewById(R.id.btnTimer6h);
        btnTimerReset = (Button) findViewById(R.id.btnTimerReset);

        // History
        layoutHistoryList = (LinearLayout) findViewById(R.id.layoutHistoryList);
        tvHistoryEmpty = (TextView) findViewById(R.id.tvHistoryEmpty);
        btnClearHistory = (Button) findViewById(R.id.btnClearHistory);
        btnExportReport = (Button) findViewById(R.id.btnExportReport);

        // Hardware
        tvAmbientTemp = (TextView) findViewById(R.id.tvAmbientTemp);
        tvBatteryTemp = (TextView) findViewById(R.id.tvBatteryTemp);
        tvSensorList = (TextView) findViewById(R.id.tvSensorList);

        // Calibration
        btnOffsetMinus = (Button) findViewById(R.id.btnOffsetMinus);
        tvOffsetVal = (TextView) findViewById(R.id.tvOffsetVal);
        btnOffsetPlus = (Button) findViewById(R.id.btnOffsetPlus);
    }

    private void setupListeners() {
        btnVoiceMute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (voiceAssistant != null) {
                    voiceAssistant.toggleMute();
                    updateVoiceButton();
                    vibrateTick();
                }
            }
        });

        btnUnitToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isFahrenheit = !isFahrenheit;
                getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(PREF_IS_FAHRENHEIT, isFahrenheit).apply();
                vibrateTick();
                updateUnitButton();
                updateHistoryView();
                recalculateAndDisplay();
            }
        });

        btnTabWrist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchMode(0); }
        });

        btnTabTemple.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchMode(1); }
        });

        btnTabWater.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchMode(2); }
        });

        btnTabRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchMode(3); }
        });

        btnStartMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startMeasurement(); }
        });

        btnCancelMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { cancelMeasurement(); }
        });

        btnRepeatMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startMeasurement(); }
        });

        btnSpeakResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (voiceAssistant != null) {
                    voiceAssistant.speakTemperature(lastMeasuredBodyWrist, isFahrenheit);
                    vibrateTick();
                }
            }
        });

        btnStartTempleMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startTempleMeasurement(); }
        });

        btnStartWaterMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startWaterMeasurement(); }
        });

        btnSpeakWaterResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (voiceAssistant != null) {
                    voiceAssistant.speakWater(lastMeasuredWater, isFahrenheit);
                    vibrateTick();
                }
            }
        });

        btnStartRoomMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startRoomMeasurement(); }
        });

        if (btnSpeakTempleResult != null) {
            btnSpeakTempleResult.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (voiceAssistant != null && lastMeasuredBodyTemple > 0) {
                        voiceAssistant.speakTemperature(lastMeasuredBodyTemple, isFahrenheit);
                        vibrateTick();
                    }
                }
            });
        }

        if (btnSpeakRoomResult != null) {
            btnSpeakRoomResult.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (voiceAssistant != null && lastMeasuredRoom > 0) {
                        voiceAssistant.speakRoom(lastMeasuredRoom, isFahrenheit);
                        vibrateTick();
                    }
                }
            });
        }

        // Pill / Medication Timers
        btnTimer4h.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setMedTimer(4); }
        });

        btnTimer6h.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { setMedTimer(6); }
        });

        btnTimerReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { resetMedTimer(); }
        });

        btnClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TempLogManager.clearHistory(MainActivity.this);
                if (sparklineView != null) {
                    sparklineView.clear();
                }
                updateHistoryView();
                vibrateTick();
            }
        });

        btnExportReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vibrateDouble();
                showClinicalReportDialog();
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

    private void setMedTimer(int hours) {
        long target = System.currentTimeMillis() + (long) hours * 3600 * 1000L;
        getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(PREF_MED_TIMER_TARGET, target).apply();

        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent intent = new Intent(this, MedicationAlarmReceiver.class);
                intent.setAction(MedicationAlarmReceiver.ACTION_MED_ALARM);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pi = PendingIntent.getBroadcast(this, 1001, intent, flags);
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
        } catch (Throwable t) {
            t.printStackTrace();
        }

        vibrateTick();
        updateMedTimerView();
        Toast.makeText(this, "💊 Таймер на " + hours + "ч установлен", Toast.LENGTH_SHORT).show();
    }

    private void resetMedTimer() {
        getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(PREF_MED_TIMER_TARGET).apply();

        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent intent = new Intent(this, MedicationAlarmReceiver.class);
                intent.setAction(MedicationAlarmReceiver.ACTION_MED_ALARM);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pi = PendingIntent.getBroadcast(this, 1001, intent, flags);
                am.cancel(pi);
            }
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(MedicationAlarmReceiver.NOTIFICATION_ID);
            }
        } catch (Throwable ignored) {}

        vibrateTick();
        updateMedTimerView();
    }

    private void updateMedTimerView() {
        if (tvMedTimerCountdown == null) return;
        long target = getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(PREF_MED_TIMER_TARGET, 0);

        if (target <= 0) {
            tvMedTimerCountdown.setText("Таймер не активен");
            tvMedTimerCountdown.setTextColor(Color.parseColor("#9E9E9E"));
            return;
        }

        long diff = target - System.currentTimeMillis();
        if (diff <= 0) {
            tvMedTimerCountdown.setText("✓ Пора принять лекарство!");
            tvMedTimerCountdown.setTextColor(Color.parseColor("#00E676"));
        } else {
            long hours = diff / 3600000;
            long mins = (diff % 3600000) / 60000;
            long secs = (diff % 60000) / 1000;
            tvMedTimerCountdown.setText(String.format(Locale.US, "💊 След. доза через: %02d:%02d:%02d", hours, mins, secs));
            tvMedTimerCountdown.setTextColor(Color.parseColor("#00E5FF"));
        }
    }

    private void updateVoiceButton() {
        if (btnVoiceMute != null && voiceAssistant != null) {
            btnVoiceMute.setImageResource(voiceAssistant.isMuted() ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
        }
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
        if (isMeasuring) cancelMeasurement();
        if (isTempleMeasuring) cancelTempleMeasurement();
        if (isWaterMeasuring) cancelWaterMeasurement();
        if (isRoomMeasuring) cancelRoomMeasurement();

        currentMode = mode;
        vibrateTick();

        btnTabWrist.setBackground(null);
        btnTabWrist.setTextColor(Color.parseColor("#9E9E9E"));
        btnTabTemple.setBackground(null);
        btnTabTemple.setTextColor(Color.parseColor("#9E9E9E"));
        btnTabWater.setBackground(null);
        btnTabWater.setTextColor(Color.parseColor("#9E9E9E"));
        btnTabRoom.setBackground(null);
        btnTabRoom.setTextColor(Color.parseColor("#9E9E9E"));

        layoutWristMode.setVisibility(View.GONE);
        layoutTempleMode.setVisibility(View.GONE);
        layoutWaterMode.setVisibility(View.GONE);
        layoutRoomMode.setVisibility(View.GONE);

        if (mode == 0) {
            btnTabWrist.setBackgroundResource(R.drawable.tab_selected_bg);
            btnTabWrist.setTextColor(Color.WHITE);
            layoutWristMode.setVisibility(View.VISIBLE);
        } else if (mode == 1) {
            btnTabTemple.setBackgroundResource(R.drawable.tab_selected_bg);
            btnTabTemple.setTextColor(Color.WHITE);
            layoutTempleMode.setVisibility(View.VISIBLE);
        } else if (mode == 2) {
            btnTabWater.setBackgroundResource(R.drawable.tab_selected_bg);
            btnTabWater.setTextColor(Color.WHITE);
            layoutWaterMode.setVisibility(View.VISIBLE);
        } else {
            btnTabRoom.setBackgroundResource(R.drawable.tab_selected_bg);
            btnTabRoom.setTextColor(Color.WHITE);
            layoutRoomMode.setVisibility(View.VISIBLE);
        }

        recalculateAndDisplay();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL);
            float density = getResources().getDisplayMetrics().density;
            int scrollPixels = Math.round(delta * density * 32);
            if (mainScrollView != null) {
                int direction = delta > 0 ? 1 : -1;
                if (mainScrollView.canScrollVertically(direction)) {
                    mainScrollView.scrollBy(0, scrollPixels);
                    long now = System.currentTimeMillis();
                    if (now - lastCrownVibeMs > 50) {
                        lastCrownVibeMs = now;
                        vibrateTick();
                    }
                }
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
            if (tvSensorList != null) tvSensorList.setText("SensorManager недоступен");
            return;
        }

        List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : allSensors) {
            String name = s.getName() != null ? s.getName().toLowerCase(Locale.US) : "";
            int type = s.getType();
            String strType = s.getStringType() != null ? s.getStringType().toLowerCase(Locale.US) : "";

            if (type == Sensor.TYPE_LIGHT) continue;

            boolean isOffbody = type == 34 || name.contains("offbody") || strType.contains("offbody");
            boolean isSkin = type == 65590 || name.contains("skin") || strType.contains("skin_temperature") || name.contains("body_temp");
            boolean isAmbient = type == Sensor.TYPE_AMBIENT_TEMPERATURE || type == 7 || name.contains("ambient_temp");
            boolean isHeartRate = type == Sensor.TYPE_HEART_RATE || type == 21 || name.contains("heart_rate");

            if (isOffbody || isSkin || isAmbient || isHeartRate) {
                registeredSensors.add(s);
            }
        }

        if (tvSensorList != null) {
            tvSensorList.setText("Xiaomi Watch 5 • Clinical v3.8.0");
        }
    }

    private void pollThermalSources() {
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (!thermalZonesDiscovered) {
                    for (int i = 0; i <= 24; i++) {
                        File typeFile = new File("/sys/class/thermal/thermal_zone" + i + "/type");
                        try (BufferedReader br = new BufferedReader(new FileReader(typeFile))) {
                            String line = br.readLine();
                            if (line != null) {
                                String type = line.trim().toLowerCase(Locale.US);
                                if (type.contains("sdm-skin-therm") || type.contains("skin")) {
                                    cachedSkinZone = i;
                                } else if (type.contains("cpuss-0") || type.contains("cpuss")) {
                                    cachedCpuZone = i;
                                } else if (type.contains("batt_gauge")) {
                                    cachedBattZone = i;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                    thermalZonesDiscovered = true;
                }

                if (cachedSkinZone >= 0) {
                    float t = readThermalZoneTemp(cachedSkinZone);
                    if (t > 0) hardwareSkinTemp = t;
                }
                if (cachedCpuZone >= 0) {
                    float t = readThermalZoneTemp(cachedCpuZone);
                    if (t > 0) hardwareCpuTemp = t;
                }
                if (cachedBattZone >= 0) {
                    float t = readThermalZoneTemp(cachedBattZone);
                    if (t > 0) hardwareBattGaugeTemp = t;
                }
            }
        });

        try {
            Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bi != null) {
                int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int plugged = bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                isPlugged = (plugged != 0);
                int temp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (temp > 0) {
                    lastBatteryTemp = temp / 10.0f;
                    tvBatteryTemp.setText(formatTemp(lastBatteryTemp));
                    checkBatteryOverheat(lastBatteryTemp);
                }
            }
        } catch (Exception ignored) {}
    }

    private float readThermalZoneTemp(int zoneIndex) {
        File tempFile = new File("/sys/class/thermal/thermal_zone" + zoneIndex + "/temp");
        try (BufferedReader br = new BufferedReader(new FileReader(tempFile))) {
            String line = br.readLine();
            if (line != null) {
                float raw = Float.parseFloat(line.trim());
                if (raw > 0) {
                    return raw > 1000 ? raw / 1000.0f : (raw > 100 ? raw / 10.0f : raw);
                }
            }
        } catch (Throwable ignored) {}
        return -1.0f;
    }

    private float getCompensatedContactTemp() {
        float raw;
        if (directSkinTemp > 0) {
            raw = directSkinTemp;
        } else if (hardwareSkinTemp > 0) {
            raw = hardwareSkinTemp;
        } else if (hardwareBattGaugeTemp > 0) {
            raw = hardwareBattGaugeTemp + (isOnWrist ? 0.4f : 0.0f);
        } else if (lastBatteryTemp > 0) {
            // When worn on wrist, battery is warmed by wrist contact (~ +0.8°C to skin surface)
            raw = lastBatteryTemp + (isOnWrist ? 0.8f : 0.0f);
        } else {
            raw = 32.5f;
        }

        if (isCharging) {
            raw -= 4.2f; // Charging coil thermal dissipation offset
        }

        if (directSkinTemp <= 0 && hardwareCpuTemp > raw && hardwareCpuTemp < 80.0f) {
            float cpuExcess = hardwareCpuTemp - raw;
            raw -= cpuExcess * 0.08f;
        }

        // PPG Heart Rate Vasodilation Compensation:
        // When skin is warm (> 33.5°C) and heart rate is elevated (> 85 bpm), peripheral capillaries dilate
        if (isOnWrist && raw > 33.5f && lastHeartRateBpm > 85.0f && lastHeartRateBpm <= 200.0f) {
            float vasoOffset = Math.min(0.40f, (lastHeartRateBpm - 85.0f) * 0.005f);
            raw -= vasoOffset;
        }

        return Math.round(raw * 10f) / 10f;
    }

    public void updateBatteryTemp(Intent intent) {
        if (intent == null) return;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
        isPlugged = (plugged != 0);
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

    private void animateBodyTempDisplay(final float targetTemp) {
        if (displayedBodyTemp <= 0) {
            displayedBodyTemp = targetTemp;
            tvSkinTemp.setText(formatTemp(targetTemp));
            return;
        }

        if (Math.abs(displayedBodyTemp - targetTemp) < 0.05f) {
            tvSkinTemp.setText(formatTemp(targetTemp));
            displayedBodyTemp = targetTemp;
            return;
        }

        if (tempAnimator != null && tempAnimator.isRunning()) {
            tempAnimator.cancel();
        }

        final float start = displayedBodyTemp;
        tempAnimator = ValueAnimator.ofFloat(start, targetTemp);
        tempAnimator.setDuration(350);
        tempAnimator.setInterpolator(new DecelerateInterpolator());
        tempAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            private float lastEmitted = start;
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float val = (float) animation.getAnimatedValue();
                displayedBodyTemp = val;
                tvSkinTemp.setText(formatTemp(val));
                if (Math.abs(val - lastEmitted) >= 0.1f) {
                    lastEmitted = val;
                    vibrateTick();
                }
            }
        });
        tempAnimator.start();
    }

    private void recalculateAndDisplay() {
        float ambientVal = (lastAmbientTemp > -40.0f && lastAmbientTemp < 70.0f) ? lastAmbientTemp : 23.5f;
        tvAmbientTemp.setText(formatTemp(ambientVal));

        float contactTemp = getCompensatedContactTemp();

        // --- Water Mode Display ---
        if (isWaterMeasuring) {
            float waterSample = hardwareSkinTemp > 0 ? hardwareSkinTemp : (directSkinTemp > 0 ? directSkinTemp : (lastBatteryTemp > 0 ? lastBatteryTemp : 28.0f));
            tvWaterTemp.setText(formatTemp(waterSample));
            updateWaterBadge(waterSample);
        } else if (lastMeasuredWater > 0 && lastMeasuredWater != 37.0f) {
            tvWaterTemp.setText(formatTemp(lastMeasuredWater));
            updateWaterBadge(lastMeasuredWater);
        } else {
            tvWaterTemp.setText("--.- " + (isFahrenheit ? "°F" : "°C"));
            if (tvWaterBadge != null) {
                tvWaterBadge.setText("🌊 Готов к замеру воды");
                tvWaterBadge.setTextColor(Color.parseColor("#00E5FF"));
            }
        }

        // --- Room Mode Display ---
        if (isRoomMeasuring) {
            float liveRoom = (!isOnWrist && !isCharging && contactTemp > 10.0f && contactTemp < 38.0f) ? contactTemp : ambientVal;
            tvRoomTemp.setText(formatTemp(liveRoom));
            updateRoomStatus(liveRoom);
        } else if (lastMeasuredRoom > 0) {
            tvRoomTemp.setText(formatTemp(lastMeasuredRoom));
            updateRoomStatus(lastMeasuredRoom);
        } else if (!isOnWrist && !isCharging && !isPlugged) {
            float liveRoom = (contactTemp > 10.0f && contactTemp < 38.0f) ? contactTemp : ambientVal;
            tvRoomTemp.setText(formatTemp(liveRoom));
            updateRoomStatus(liveRoom);
        } else {
            tvRoomTemp.setText("--.- " + (isFahrenheit ? "°F" : "°C"));
            if (tvRoomStatus != null) {
                tvRoomStatus.setText("⚠️ Снимите часы и положите на стол для замера");
                tvRoomStatus.setTextColor(Color.parseColor("#FFA000"));
            }
        }

        // --- Temple Mode Display ---
        if (isTempleMeasuring) {
            float liveTempleSkin = contactTemp;
            long nowT = System.currentTimeMillis();
            float circBase = CircadianModel.getBaseline(nowT);
            float templeDelta = liveTempleSkin - 34.5f;
            float liveTempleBody = circBase + (templeDelta * 0.70f) + tempOffset;
            if (liveTempleBody < 34.0f) liveTempleBody = 34.0f;
            if (liveTempleBody > 42.0f) liveTempleBody = 42.0f;
            liveTempleBody = Math.round(liveTempleBody * 10f) / 10f;
            tvTempleTemp.setText(formatTemp(liveTempleBody));
            if (mercuryViewTemple != null) {
                mercuryViewTemple.setTemperature(liveTempleBody, true);
            }
        } else if (lastMeasuredBodyTemple > 0 && lastMeasuredBodyTemple != 36.6f) {
            tvTempleTemp.setText(formatTemp(lastMeasuredBodyTemple));
            if (mercuryViewTemple != null) {
                mercuryViewTemple.setTemperature(lastMeasuredBodyTemple, true);
            }
        } else {
            tvTempleTemp.setText("--.- " + (isFahrenheit ? "°F" : "°C"));
            if (mercuryViewTemple != null) {
                mercuryViewTemple.setTemperature(34.0f, true);
            }
        }

        // --- Wrist Mode Processing ---
        long now = System.currentTimeMillis();
        boolean hasHeartRatePulse = (lastHeartRateBpm >= 40.0f && (now - lastHeartRateTimestampMs < 8000));
        boolean reallyOnWrist = !isPlugged && !isCharging && (isOnWrist || hasHeartRatePulse) && (contactTemp >= 16.0f);
        if (!reallyOnWrist) {
            kalmanSkin.reset(-100.0f);
            kalmanBody.reset(-100.0f);
            displayedBodyTemp = -1.0f;
            currentCalculatedSkin = contactTemp;
            currentCalculatedBody = 35.0f;
            tvSkinTemp.setText("--.- " + (isFahrenheit ? "°F" : "°C"));
            tvSkinTemp.setTextColor(Color.parseColor("#78909C"));

            if (isPlugged || isCharging) {
                tvSkinStatus.setText("⚡ На зарядке • Снимите для замера");
                tvSkinStatus.setTextColor(Color.parseColor("#00E5FF"));
            } else {
                tvSkinStatus.setText("⚠️ СНЯТЫ С РУКИ • Наденьте часы");
                tvSkinStatus.setTextColor(Color.parseColor("#FFA000"));
            }

            if (tvSensorDetail != null) {
                tvSensorDetail.setText(String.format(Locale.US, "Корпус: %s • Среда: %s", formatTemp(contactTemp), formatTemp(ambientVal)));
            }
            if (mercuryViewWrist != null) {
                mercuryViewWrist.setTemperature(34.0f, true);
            }
            updateThermalBalance(contactTemp, ambientVal);
            updateFeverAdvisorOffbody();
            return;
        }

        // On-wrist Dual-Flux physiological model (Heat Flux Gradient Compensation)
        // Filter raw contact temperature directly without contaminating with user offset first
        float filteredSkin = kalmanSkin.update(contactTemp);

        // Circadian Baseline & Harmonic Computation
        now = System.currentTimeMillis();
        float circadianBase = CircadianModel.getBaseline(now);

        // Smooth bounded non-linear environmental compensation (tanh saturation, max ±0.45°C)
        float rawAmbDelta = (float) (Math.tanh((23.5f - ambientVal) / 10.0f) * 0.45f);
        float ambientDelta = Math.max(-0.5f, Math.min(0.5f, rawAmbDelta));

        float skinDelta = filteredSkin - 33.0f;
        // Compensate for nighttime peripheral vasodilation during sleep phase
        if (CircadianModel.isNightSleepPhase(now) && skinDelta > 0) {
            skinDelta *= 0.35f;
        }

        float skinFactor = (filteredSkin > 33.5f) ? 0.50f : 0.40f;
        // User calibration offset applied strictly once to final body temperature
        float calculatedBody = circadianBase + (skinDelta * skinFactor) + ambientDelta + tempOffset;

        // Realistic safety bounds for human physiology (34.0 to 42.0)
        if (calculatedBody < 34.0f) calculatedBody = 34.0f;
        if (calculatedBody > 42.0f) calculatedBody = 42.0f;

        float filteredBody = kalmanBody.update(calculatedBody);
        filteredBody = Math.round(filteredBody * 10f) / 10f;

        currentCalculatedSkin = filteredSkin;
        currentCalculatedBody = filteredBody;

        // Circadian Deviation (Apple/Garmin Baseline Tracking)
        float circadianDev = CircadianModel.getDeviation(filteredBody, now);
        String devStr = CircadianModel.formatDeviation(circadianDev);

        // Primary Display: Smooth kinetic odometer animation
        animateBodyTempDisplay(filteredBody);

        // Clinical classification + Circadian alignment
        if (filteredBody < 35.5f) {
            tvSkinTemp.setTextColor(Color.parseColor("#00E5FF"));
            tvSkinStatus.setText(String.format(Locale.US, "❄️ %s • Пониженная (Ритм %s)", formatTempShort(filteredBody), devStr));
            tvSkinStatus.setTextColor(Color.parseColor("#00E5FF"));
        } else if (filteredBody <= 37.2f) {
            tvSkinTemp.setTextColor(Color.parseColor("#00E676"));
            tvSkinStatus.setText(String.format(Locale.US, "✓ %s • В норме (Ритм %s)", formatTempShort(filteredBody), devStr));
            tvSkinStatus.setTextColor(Color.parseColor("#00E676"));
        } else if (filteredBody <= 38.0f) {
            tvSkinTemp.setTextColor(Color.parseColor("#FFD600"));
            tvSkinStatus.setText(String.format(Locale.US, "⚠️ %s • Субфебрилитет (%s к норме)", formatTempShort(filteredBody), devStr));
            tvSkinStatus.setTextColor(Color.parseColor("#FFD600"));
        } else {
            tvSkinTemp.setTextColor(Color.parseColor("#FF5252"));
            tvSkinStatus.setText(String.format(Locale.US, "🔥 %s • Лихорадка! (%s)", formatTempShort(filteredBody), devStr));
            tvSkinStatus.setTextColor(Color.parseColor("#FF5252"));
        }

        if (tvSensorDetail != null) {
            String hrStr = lastHeartRateBpm > 0 ? String.format(Locale.US, " • Пульс %d", Math.round(lastHeartRateBpm)) : "";
            tvSensorDetail.setText(String.format(Locale.US, "Кожа: %s • База суток: %.1f°%s", 
                    formatTemp(filteredSkin), circadianBase, hrStr));
        }

        if (mercuryViewWrist != null) {
            mercuryViewWrist.setTemperature(filteredBody, true);
        }

        savePrefsAndUpdateComplication(filteredSkin, filteredBody, ambientVal);
        updateSparkline(filteredBody);
        updateThermalBalance(filteredSkin, ambientVal);
        updateFeverAdvisor(filteredBody);
    }

    private void updateRoomStatus(float roomTemp) {
        if (tvRoomStatus == null) return;
        if (roomTemp < 18.0f) {
            tvRoomStatus.setText("❄️ Прохладный микроклимат");
            tvRoomStatus.setTextColor(Color.parseColor("#00E5FF"));
        } else if (roomTemp <= 24.5f) {
            tvRoomStatus.setText("✓ Комфортная температура");
            tvRoomStatus.setTextColor(Color.parseColor("#00E676"));
        } else {
            tvRoomStatus.setText("🔥 Тёплое / душное помещение");
            tvRoomStatus.setTextColor(Color.parseColor("#FF9100"));
        }
    }

    private void updateFeverAdvisorOffbody() {
        if (tvAdvisorStatus == null || tvAdvisorAdvice == null) return;
        List<TempLogManager.Entry> history = TempLogManager.getHistory(this);
        if (!history.isEmpty() && (history.get(0).mode == 0 || history.get(0).mode == 1)) {
            TempLogManager.Entry last = history.get(0);
            tvAdvisorStatus.setText("Предыдущий замер: " + formatTempShort(last.tempBody));
            tvAdvisorStatus.setTextColor(Color.parseColor("#9E9E9E"));
            tvAdvisorAdvice.setText("Для обновления советов и свежей температуры наденьте часы на руку.");
        } else {
            tvAdvisorStatus.setText("ℹ️ Ожидание замера");
            tvAdvisorStatus.setTextColor(Color.parseColor("#9E9E9E"));
            tvAdvisorAdvice.setText("Наденьте часы на руку или выполните замер на виске.");
        }
    }

    private void updateWaterBadge(float tempC) {
        if (tvWaterBadge == null) return;
        if (tempC < 15.0f) {
            tvWaterBadge.setText("🧊 Ледяная купель (Моржевание)");
            tvWaterBadge.setTextColor(Color.parseColor("#00E5FF"));
        } else if (tempC <= 28.5f) {
            tvWaterBadge.setText("🏊 Бодрящая вода (Бассейн)");
            tvWaterBadge.setTextColor(Color.parseColor("#00B0FF"));
        } else if (tempC <= 35.9f) {
            tvWaterBadge.setText("🌊 Приятная теплая вода");
            tvWaterBadge.setTextColor(Color.parseColor("#00E676"));
        } else if (tempC <= 38.5f) {
            tvWaterBadge.setText("👶 Идеально для детской ванны!");
            tvWaterBadge.setTextColor(Color.parseColor("#FFD600"));
        } else if (tempC <= 41.5f) {
            tvWaterBadge.setText("♨️ Горячая релакс-ванна");
            tvWaterBadge.setTextColor(Color.parseColor("#FF9100"));
        } else {
            tvWaterBadge.setText("🔥 Опасность ожога! Слишком горячо!");
            tvWaterBadge.setTextColor(Color.parseColor("#FF1744"));
        }
    }

    private void updateFeverAdvisor(float bodyTemp) {
        if (tvAdvisorStatus == null || tvAdvisorAdvice == null) return;

        if (bodyTemp < 35.5f) {
            tvAdvisorStatus.setText("❄️ Гипотермия (" + formatTempShort(bodyTemp) + ")");
            tvAdvisorStatus.setTextColor(Color.parseColor("#00E5FF"));
            tvAdvisorAdvice.setText("Пониженная температура. Рекомендуется согреться, выпить горячий чай и отдохнуть.");
        } else if (bodyTemp <= 37.2f) {
            tvAdvisorStatus.setText("✓ Норма (" + formatTempShort(bodyTemp) + ")");
            tvAdvisorStatus.setTextColor(Color.parseColor("#00E676"));
            tvAdvisorAdvice.setText("Организм в отличном балансе. Прием жаропонижающих не требуется.");
        } else if (bodyTemp <= 38.0f) {
            tvAdvisorStatus.setText("⚠️ Субфебрилитет (" + formatTempShort(bodyTemp) + ")");
            tvAdvisorStatus.setTextColor(Color.parseColor("#FFA000"));
            tvAdvisorAdvice.setText("Иммунитет борется сам. Сбивать лекарствами не нужно. Пейте больше теплой воды или морса.");
        } else if (bodyTemp <= 39.0f) {
            tvAdvisorStatus.setText("🔥 Умеренная лихорадка (" + formatTempShort(bodyTemp) + ")");
            tvAdvisorStatus.setTextColor(Color.parseColor("#FF7A00"));
            tvAdvisorAdvice.setText("При плохом самочувствии: парацетамол (500 мг) или ибупрофен (400 мг). Не укутывайтесь.");
        } else {
            tvAdvisorStatus.setText("🚨 Высокая температура! (" + formatTempShort(bodyTemp) + ")");
            tvAdvisorStatus.setTextColor(Color.parseColor("#FF5252"));
            tvAdvisorAdvice.setText("Примите жаропонижающее, прохладный компресс на лоб. Если держится — вызовите врача!");
        }
    }

    private void updateThermalBalance(float skinVal, float ambientVal) {
        if (tvDeltaT == null) return;
        float amb = ambientVal > 0 ? ambientVal : (lastBatteryTemp > 0 ? lastBatteryTemp - 2.5f : 24.0f);
        float delta = skinVal - amb;

        if (isFahrenheit) {
            float deltaF = delta * 1.8f;
            tvDeltaT.setText(String.format(Locale.US, "%+.1f °F", deltaF));
        } else {
            tvDeltaT.setText(String.format(Locale.US, "%+.1f °C", delta));
        }

        if (delta < 5.0f) {
            tvThermalStatus.setText("❄️ Спазм сосудов (озноб/холод)");
            tvThermalStatus.setTextColor(Color.parseColor("#00E5FF"));
            tvThermalDesc.setText("Периферические сосуды сужены");
        } else if (delta <= 12.0f) {
            tvThermalStatus.setText("🌿 Оптимальный тонус сосудов");
            tvThermalStatus.setTextColor(Color.parseColor("#00E676"));
            tvThermalDesc.setText("Идеальный градиент терморегуляции");
        } else {
            tvThermalStatus.setText("🔥 Активная теплоотдача (жар)");
            tvThermalStatus.setTextColor(Color.parseColor("#FF7A00"));
            tvThermalDesc.setText("Усиленный кровоток периферии");
        }

        // Physiological Thermogenesis Calorie Burn calculation
        if (tvThermogenesisCalories != null) {
            int kcalPerHour = (int) Math.round(12.0f + 1.2f * Math.max(0.0f, delta));
            tvThermogenesisCalories.setText(String.format(Locale.US, "⚡ Обогрев тела: ~%d ккал/ч на термогенез", kcalPerHour));
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

    // --- Wrist Measurement (30 seconds) ---
    private void startMeasurement() {
        if (!isOnWrist && directSkinTemp <= 0) {
            Toast.makeText(this, "Пожалуйста, наденьте часы на руку!", Toast.LENGTH_SHORT).show();
            return;
        }

        setWakeLockActive(true);
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
        setWakeLockActive(false);
        isMeasuring = false;
        vibrateDouble();

        layoutMeasuring.setVisibility(View.GONE);
        layoutMeasureResult.setVisibility(View.VISIBLE);

        float finalSkin = currentCalculatedSkin > 20.0f ? currentCalculatedSkin : 33.0f;
        if (!measureSamples.isEmpty()) {
            int count = Math.min(measureSamples.size(), 8);
            float sum = 0;
            for (int i = measureSamples.size() - count; i < measureSamples.size(); i++) {
                sum += measureSamples.get(i);
            }
            finalSkin = sum / count;

            // Thermal saturation compensation for ceramic/glass case
            if (measureSamples.size() >= 10) {
                float slope = (measureSamples.get(measureSamples.size() - 1) - measureSamples.get(measureSamples.size() - 6)) / 5.0f;
                if (slope > 0.02f) {
                    finalSkin += Math.min(0.8f, slope * 5.0f);
                }
            }
        }

        long now = System.currentTimeMillis();
        float circadianBase = CircadianModel.getBaseline(now);
        float ambientVal = (lastAmbientTemp > -40.0f && lastAmbientTemp < 70.0f) ? lastAmbientTemp : 23.5f;
        float rawAmbDelta = (float) (Math.tanh((23.5f - ambientVal) / 10.0f) * 0.45f);
        float ambientDelta = Math.max(-0.5f, Math.min(0.5f, rawAmbDelta));
        float skinDelta = finalSkin - 33.0f;
        if (CircadianModel.isNightSleepPhase(now) && skinDelta > 0) {
            skinDelta *= 0.35f;
        }
        float skinFactor = (finalSkin > 33.5f) ? 0.50f : 0.40f;
        float finalBody = circadianBase + (skinDelta * skinFactor) + ambientDelta + tempOffset;
        if (finalBody < 34.0f) finalBody = 34.0f;
        if (finalBody > 42.0f) finalBody = 42.0f;
        finalBody = Math.round(finalBody * 10f) / 10f;

        float circadianDev = CircadianModel.getDeviation(finalBody, now);
        String devStr = CircadianModel.formatDeviation(circadianDev);

        lastMeasuredBodyWrist = finalBody;
        currentCalculatedBody = finalBody;
        tvFixedResult.setText(formatTemp(finalBody));
        animateBodyTempDisplay(finalBody);

        if (finalBody < 35.5f) {
            tvResultBadge.setText(String.format(Locale.US, "Пониженная (%s к ритму)", devStr));
            tvResultBadge.setTextColor(Color.parseColor("#00E5FF"));
            tvFixedResult.setTextColor(Color.parseColor("#00E5FF"));
            tvSkinTemp.setTextColor(Color.parseColor("#00E5FF"));
            tvSkinStatus.setText(String.format(Locale.US, "❄️ %s • Пониженная (Ритм %s)", formatTempShort(finalBody), devStr));
            tvSkinStatus.setTextColor(Color.parseColor("#00E5FF"));
        } else if (finalBody <= 37.2f) {
            tvResultBadge.setText(String.format(Locale.US, "✓ В норме (%s к ритму)", devStr));
            tvResultBadge.setTextColor(Color.parseColor("#00E676"));
            tvFixedResult.setTextColor(Color.parseColor("#00E676"));
            tvSkinTemp.setTextColor(Color.parseColor("#00E676"));
            tvSkinStatus.setText(String.format(Locale.US, "✓ %s • В норме (Ритм %s)", formatTempShort(finalBody), devStr));
            tvSkinStatus.setTextColor(Color.parseColor("#00E676"));
        } else if (finalBody <= 38.0f) {
            tvResultBadge.setText(String.format(Locale.US, "⚠ Субфебрилитет (%s к норме)", devStr));
            tvResultBadge.setTextColor(Color.parseColor("#FFD600"));
            tvFixedResult.setTextColor(Color.parseColor("#FFD600"));
            tvSkinTemp.setTextColor(Color.parseColor("#FFD600"));
            tvSkinStatus.setText(String.format(Locale.US, "⚠️ %s • Субфебрилитет (%s)", formatTempShort(finalBody), devStr));
            tvSkinStatus.setTextColor(Color.parseColor("#FFD600"));
        } else {
            tvResultBadge.setText(String.format(Locale.US, "🔥 Лихорадка! (%s)", devStr));
            tvResultBadge.setTextColor(Color.parseColor("#FF5252"));
            tvFixedResult.setTextColor(Color.parseColor("#FF5252"));
            tvSkinTemp.setTextColor(Color.parseColor("#FF5252"));
            tvSkinStatus.setText(String.format(Locale.US, "🔥 %s • Лихорадка! (%s)", formatTempShort(finalBody), devStr));
            tvSkinStatus.setTextColor(Color.parseColor("#FF5252"));
        }

        if (tvSensorDetail != null) {
            String hrStr = lastHeartRateBpm > 0 ? String.format(Locale.US, " • Пульс %d", Math.round(lastHeartRateBpm)) : "";
            tvSensorDetail.setText(String.format(Locale.US, "Кожа: %s • База суток: %.1f°%s", 
                    formatTemp(finalSkin), circadianBase, hrStr));
        }

        if (mercuryViewWrist != null) {
            mercuryViewWrist.setTemperature(finalBody, true);
        }

        if (voiceAssistant != null) {
            voiceAssistant.speakTemperature(finalBody, isFahrenheit);
        }

        TempLogManager.saveRecord(this, finalBody, finalSkin, 0);
        updateHistoryView();
        updateFeverAdvisor(finalBody);
        forceComplicationUpdate(finalSkin, finalBody, ambientVal);
    }

    private void cancelMeasurement() {
        setWakeLockActive(false);
        isMeasuring = false;
        uiHandler.removeCallbacks(measureCountdownRunnable);
        layoutMeasuring.setVisibility(View.GONE);
        btnStartMeasure.setVisibility(View.VISIBLE);
    }

    private void cancelTempleMeasurement() {
        setWakeLockActive(false);
        isTempleMeasuring = false;
        uiHandler.removeCallbacks(templeCountdownRunnable);
        if (tvTempleCountdown != null) tvTempleCountdown.setVisibility(View.GONE);
        if (btnStartTempleMeasure != null) btnStartTempleMeasure.setVisibility(View.VISIBLE);
    }

    private void cancelWaterMeasurement() {
        setWakeLockActive(false);
        isWaterMeasuring = false;
        uiHandler.removeCallbacks(waterCountdownRunnable);
        if (tvWaterCountdown != null) tvWaterCountdown.setVisibility(View.GONE);
        if (btnStartWaterMeasure != null) btnStartWaterMeasure.setVisibility(View.VISIBLE);
    }

    private void cancelRoomMeasurement() {
        setWakeLockActive(false);
        isRoomMeasuring = false;
        uiHandler.removeCallbacks(roomCountdownRunnable);
        if (tvRoomCountdown != null) tvRoomCountdown.setVisibility(View.GONE);
        if (btnStartRoomMeasure != null) btnStartRoomMeasure.setVisibility(View.VISIBLE);
    }

    // --- Temple / Forehead Measurement (15 seconds) ---
    private void startTempleMeasurement() {
        setWakeLockActive(true);
        isTempleMeasuring = true;
        templeSecondsLeft = 15;
        templeSamples.clear();

        btnStartTempleMeasure.setVisibility(View.GONE);
        layoutTempleResult.setVisibility(View.GONE);
        tvTempleCountdown.setVisibility(View.VISIBLE);
        tvTempleCountdown.setText("15 сек... Держите у виска");

        vibrateShort();

        uiHandler.removeCallbacks(templeCountdownRunnable);
        uiHandler.postDelayed(templeCountdownRunnable, 1000);
    }

    private void tickTempleMeasure() {
        if (!isTempleMeasuring) return;

        templeSecondsLeft--;
        vibrateTick();

        float sample = hardwareSkinTemp > 0 ? hardwareSkinTemp : (directSkinTemp > 0 ? directSkinTemp : (lastBatteryTemp > 0 ? lastBatteryTemp : 35.8f));
        templeSamples.add(sample);

        tvTempleCountdown.setText(templeSecondsLeft + " сек... Держите у виска");

        if (templeSecondsLeft > 0) {
            uiHandler.postDelayed(templeCountdownRunnable, 1000);
        } else {
            finishTempleMeasurement();
        }
    }

    private void finishTempleMeasurement() {
        setWakeLockActive(false);
        isTempleMeasuring = false;
        vibrateDouble();

        tvTempleCountdown.setVisibility(View.GONE);
        layoutTempleResult.setVisibility(View.VISIBLE);
        btnStartTempleMeasure.setVisibility(View.VISIBLE);
        btnStartTempleMeasure.setText("⏱️ ПОВТОРИТЬ ЗАМЕР НА ВИСКЕ");

        float templeSkin = 35.8f;
        if (!templeSamples.isEmpty()) {
            float sum = 0;
            for (float s : templeSamples) sum += s;
            templeSkin = sum / templeSamples.size();

            // Thermal inertia extrapolation for temple contact
            if (templeSamples.size() >= 5) {
                float slope = (templeSamples.get(templeSamples.size() - 1) - templeSamples.get(0)) / (float) templeSamples.size();
                if (slope > 0.02f) {
                    templeSkin += Math.min(1.0f, slope * 8.0f);
                }
            }
        }

        long now = System.currentTimeMillis();
        float circBase = CircadianModel.getBaseline(now);
        // Temporal artery core conversion: forehead skin delta anchored to circadian baseline
        float templeDelta = templeSkin - 34.5f;
        float templeBody = circBase + (templeDelta * 0.70f) + tempOffset;
        if (templeBody < 34.0f) templeBody = 34.0f;
        if (templeBody > 42.0f) templeBody = 42.0f;
        templeBody = Math.round(templeBody * 10f) / 10f;

        lastMeasuredBodyTemple = templeBody;
        tvTempleFixedResult.setText(formatTemp(templeBody));
        tvTempleTemp.setText(formatTemp(templeBody));

        if (templeBody < 35.5f) {
            tvTempleResultBadge.setText("Пониженная (Височная артерия)");
            tvTempleResultBadge.setTextColor(Color.parseColor("#00E5FF"));
            tvTempleFixedResult.setTextColor(Color.parseColor("#00E5FF"));
        } else if (templeBody <= 37.2f) {
            tvTempleResultBadge.setText("✓ Норма (Височная артерия)");
            tvTempleResultBadge.setTextColor(Color.parseColor("#00E676"));
            tvTempleFixedResult.setTextColor(Color.parseColor("#00E676"));
        } else if (templeBody <= 38.0f) {
            tvTempleResultBadge.setText("⚠ Субфебрильная (Висок)");
            tvTempleResultBadge.setTextColor(Color.parseColor("#FFA000"));
            tvTempleFixedResult.setTextColor(Color.parseColor("#FFA000"));
        } else {
            tvTempleResultBadge.setText("🔥 Повышенная (Висок)");
            tvTempleResultBadge.setTextColor(Color.parseColor("#FF5252"));
            tvTempleFixedResult.setTextColor(Color.parseColor("#FF5252"));
        }

        if (mercuryViewTemple != null) {
            mercuryViewTemple.setTemperature(templeBody, true);
        }

        if (voiceAssistant != null) {
            voiceAssistant.speakTemperature(templeBody, isFahrenheit);
        }

        TempLogManager.saveRecord(this, templeBody, templeSkin, 1);
        updateHistoryView();
        updateFeverAdvisor(templeBody);

        forceComplicationUpdate(templeSkin, templeBody, lastAmbientTemp);
    }

    // --- Water / Bath Measurement (10 seconds) ---
    private void startWaterMeasurement() {
        setWakeLockActive(true);
        isWaterMeasuring = true;
        waterSecondsLeft = 10;
        waterSamples.clear();

        btnStartWaterMeasure.setVisibility(View.GONE);
        layoutWaterResult.setVisibility(View.GONE);
        tvWaterCountdown.setVisibility(View.VISIBLE);
        tvWaterCountdown.setText("10 сек... Держите часы в воде");

        vibrateShort();

        uiHandler.removeCallbacks(waterCountdownRunnable);
        uiHandler.postDelayed(waterCountdownRunnable, 1000);
    }

    private void tickWaterMeasure() {
        if (!isWaterMeasuring) return;

        waterSecondsLeft--;
        vibrateTick();

        float sample = hardwareSkinTemp > 0 ? hardwareSkinTemp : (directSkinTemp > 0 ? directSkinTemp : (lastBatteryTemp > 0 ? lastBatteryTemp : 28.0f));
        waterSamples.add(sample);

        tvWaterCountdown.setText(waterSecondsLeft + " сек... Держите часы в воде");

        if (waterSecondsLeft > 0) {
            uiHandler.postDelayed(waterCountdownRunnable, 1000);
        } else {
            finishWaterMeasurement();
        }
    }

    private void finishWaterMeasurement() {
        setWakeLockActive(false);
        isWaterMeasuring = false;
        vibrateDouble();

        tvWaterCountdown.setVisibility(View.GONE);
        layoutWaterResult.setVisibility(View.VISIBLE);
        btnStartWaterMeasure.setVisibility(View.VISIBLE);
        btnStartWaterMeasure.setText("⏱️ ПОВТОРИТЬ ЗАМЕР ВОДЫ");

        float waterVal = 28.0f;
        if (!waterSamples.isEmpty()) {
            float sum = 0;
            for (float s : waterSamples) sum += s;
            waterVal = sum / waterSamples.size();

            // Water thermal conductivity extrapolation
            if (waterSamples.size() >= 4) {
                float slope = waterSamples.get(waterSamples.size() - 1) - waterSamples.get(0);
                if (Math.abs(slope) > 0.3f) {
                    waterVal = waterSamples.get(waterSamples.size() - 1) + slope * 0.4f;
                }
            }
        }

        lastMeasuredWater = waterVal;
        tvWaterFixedResult.setText(formatTemp(waterVal));
        updateWaterBadge(waterVal);

        if (voiceAssistant != null) {
            voiceAssistant.speakWater(waterVal, isFahrenheit);
        }

        TempLogManager.saveRecord(this, waterVal, waterVal, 2);
        updateHistoryView();
    }

    // --- Room Measurement (60 seconds) ---
    private void startRoomMeasurement() {
        setWakeLockActive(true);
        isRoomMeasuring = true;
        roomSecondsLeft = 60;
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
            setWakeLockActive(false);
            isRoomMeasuring = false;
            vibrateDouble();
            tvRoomCountdown.setText("✓ Замер зафиксирован");
            btnStartRoomMeasure.setText("⏱️ Повторить замер воздуха");
            btnStartRoomMeasure.setVisibility(View.VISIBLE);

            float ambientVal = lastAmbientTemp > 0 ? lastAmbientTemp : (lastBatteryTemp > 0 ? lastBatteryTemp - 2.5f : 24.0f);
            ambientVal = Math.round(ambientVal * 10f) / 10f;
            lastMeasuredRoom = ambientVal;

            if (layoutRoomResult != null) {
                layoutRoomResult.setVisibility(View.VISIBLE);
            }
            if (tvRoomFixedResult != null) {
                tvRoomFixedResult.setText(formatTemp(ambientVal));
            }
            if (tvRoomResultBadge != null) {
                if (ambientVal < 18.0f) {
                    tvRoomResultBadge.setText("❄️ Прохладный микроклимат");
                    tvRoomResultBadge.setTextColor(Color.parseColor("#00E5FF"));
                } else if (ambientVal <= 24.5f) {
                    tvRoomResultBadge.setText("✓ Комфортный микроклимат");
                    tvRoomResultBadge.setTextColor(Color.parseColor("#00E676"));
                } else {
                    tvRoomResultBadge.setText("🔥 Тёплое / душное помещение");
                    tvRoomResultBadge.setTextColor(Color.parseColor("#FF9100"));
                }
            }

            if (voiceAssistant != null) {
                voiceAssistant.speakRoom(ambientVal, isFahrenheit);
            }

            TempLogManager.saveRecord(this, ambientVal, ambientVal, 3);
            updateHistoryView();
        }
    }

    // --- History View Rendering ---
    private void showClinicalReportDialog() {
        if (clinicalDialog != null && clinicalDialog.isShowing()) {
            clinicalDialog.dismiss();
        }
        TempLogManager.ClinicalReport report = TempLogManager.buildClinicalReport(this, isFahrenheit);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("📊 Сводка за 24 часа");
        builder.setMessage(report.formattedReport);
        builder.setPositiveButton("Понятно", null);
        clinicalDialog = builder.create();
        clinicalDialog.show();
    }

    private void updateHistoryView() {
        if (layoutHistoryList == null) return;

        List<TempLogManager.Entry> entries = TempLogManager.getHistory(this);

        layoutHistoryList.removeAllViews();

        if (entries.isEmpty()) {
            if (tvHistoryEmpty != null) {
                tvHistoryEmpty.setVisibility(View.VISIBLE);
                layoutHistoryList.addView(tvHistoryEmpty);
            }
            return;
        }

        if (tvHistoryEmpty != null) {
            tvHistoryEmpty.setVisibility(View.GONE);
        }

        for (int i = 0; i < entries.size(); i++) {
            TempLogManager.Entry e = entries.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 4, 0, 4);

            TextView tvLeft = new TextView(this);
            tvLeft.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            tvLeft.setText(String.format(Locale.getDefault(), "[%s] %s", e.getModeLabel(), e.getFormattedTime()));
            tvLeft.setTextColor(Color.parseColor("#9E9E9E"));
            tvLeft.setTextSize(9f);

            TextView tvRight = new TextView(this);
            String deltaStr = "";
            float dVal = isFahrenheit ? (e.deltaPrev * 1.8f) : e.deltaPrev;
            if (e.deltaPrev > 0.05f) {
                deltaStr = String.format(Locale.US, " (+%.1f°↑)", dVal);
            } else if (e.deltaPrev < -0.05f) {
                deltaStr = String.format(Locale.US, " (%.1f°↓)", dVal);
            } else {
                deltaStr = " (=)";
            }

            tvRight.setText(formatTempShort(e.tempBody) + deltaStr);
            if (e.mode == 2) {
                tvRight.setTextColor(Color.parseColor("#00E5FF"));
            } else if (e.tempBody > 37.2f) {
                tvRight.setTextColor(Color.parseColor("#FFA000"));
            } else if (e.tempBody < 35.5f) {
                tvRight.setTextColor(Color.parseColor("#00E5FF"));
            } else {
                tvRight.setTextColor(Color.parseColor("#00E676"));
            }
            tvRight.setTextSize(10f);

            row.addView(tvLeft);
            row.addView(tvRight);
            layoutHistoryList.addView(row);
        }
    }

    private void savePrefsAndUpdateComplication(float skinTemp, float bodyEst, float ambientTemp) {
        try {
            long now = System.currentTimeMillis();
            // Throttle disk writes and IPC broadcast: max once per 60s or on >= 0.2°C shift
            if (now - lastComplicationBroadcastMs > 60000 || Math.abs(bodyEst - lastBroadcastBody) >= 0.2f) {
                lastComplicationBroadcastMs = now;
                lastBroadcastBody = bodyEst;
                getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putFloat(ThermometerComplicationService.KEY_SKIN_TEMP, skinTemp)
                        .putFloat(ThermometerComplicationService.KEY_BODY_EST, bodyEst)
                        .putFloat(ThermometerComplicationService.KEY_AMBIENT_TEMP, ambientTemp)
                        .putLong(ThermometerComplicationService.KEY_TIMESTAMP, now)
                        .apply();

                ThermometerComplicationService.requestComplicationUpdate(this);
            }
        } catch (Exception ignored) {}
    }

    private void forceComplicationUpdate(float skinTemp, float bodyEst, float ambientTemp) {
        lastComplicationBroadcastMs = 0;
        savePrefsAndUpdateComplication(skinTemp, bodyEst, ambientTemp);
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

    private void vibrateHeartbeat() {
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 25, 60, 45}, -1));
                } else {
                    vibrator.vibrate(new long[]{0, 25, 60, 45}, -1);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mainScrollView != null) {
            mainScrollView.requestFocus();
        }
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
        setWakeLockActive(false);
        if (isMeasuring) cancelMeasurement();
        if (isTempleMeasuring) {
            isTempleMeasuring = false;
            uiHandler.removeCallbacks(templeCountdownRunnable);
            tvTempleCountdown.setVisibility(View.GONE);
            btnStartTempleMeasure.setVisibility(View.VISIBLE);
        }
        if (isWaterMeasuring) {
            isWaterMeasuring = false;
            uiHandler.removeCallbacks(waterCountdownRunnable);
            tvWaterCountdown.setVisibility(View.GONE);
            btnStartWaterMeasure.setVisibility(View.VISIBLE);
        }
        if (isRoomMeasuring) {
            cancelRoomMeasurement();
        }
        if (tempAnimator != null && tempAnimator.isRunning()) {
            tempAnimator.cancel();
        }
        if (mercuryViewWrist != null) {
            mercuryViewWrist.cancelAnimation();
        }
        if (mercuryViewTemple != null) {
            mercuryViewTemple.cancelAnimation();
        }
        if (voiceAssistant != null) {
            voiceAssistant.stop();
        }
        if (clinicalDialog != null && clinicalDialog.isShowing()) {
            clinicalDialog.dismiss();
            clinicalDialog = null;
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
    protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        setWakeLockActive(false);
        if (clinicalDialog != null && clinicalDialog.isShowing()) {
            clinicalDialog.dismiss();
            clinicalDialog = null;
        }
        if (ioExecutor != null && !ioExecutor.isShutdown()) {
            ioExecutor.shutdownNow();
        }
        if (tempAnimator != null) {
            tempAnimator.cancel();
        }
        if (mercuryViewWrist != null) {
            mercuryViewWrist.cancelAnimation();
        }
        if (mercuryViewTemple != null) {
            mercuryViewTemple.cancelAnimation();
        }
        if (voiceAssistant != null) {
            voiceAssistant.destroy();
            voiceAssistant = null;
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

        if (type == 34 || name.contains("offbody") || strType.contains("offbody")) {
            isOnWrist = (value > 0.5f);
            if (!isOnWrist) {
                lastHeartRateBpm = -1.0f;
                lastHeartRateTimestampMs = 0;
            }
            recalculateAndDisplay();
            return;
        }

        if (type == Sensor.TYPE_HEART_RATE || type == 21 || name.contains("heart_rate")) {
            if (value >= 35.0f && value <= 220.0f) {
                lastHeartRateBpm = value;
                lastHeartRateTimestampMs = System.currentTimeMillis();
                recalculateAndDisplay();
            }
            return;
        }

        if (type == 65590 || name.contains("skin") || strType.contains("skin_temperature") || name.contains("body_temp")) {
            if (value > 10.0f && value < 50.0f) {
                directSkinTemp = value;
                recalculateAndDisplay();
            }
        } else if (type == Sensor.TYPE_AMBIENT_TEMPERATURE || type == 7 || name.contains("ambient_temp")) {
            if (value >= -30.0f && value <= 70.0f) {
                lastAmbientTemp = value;
                recalculateAndDisplay();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
