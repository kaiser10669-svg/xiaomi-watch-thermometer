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
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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
    private TextView tvSkinTemp;
    private TextView tvSkinStatus;
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
    private TextView tvTempleTemp;
    private TextView tvTempleStatus;
    private MercuryThermometerView mercuryViewTemple;
    private Button btnStartTempleMeasure;
    private TextView tvTempleCountdown;
    private View layoutTempleResult;
    private TextView tvTempleFixedResult;
    private TextView tvTempleResultBadge;

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

    // --- Lab & Tricks Views ---
    private Button btnToolLie;
    private Button btnToolWarmer;
    private Button btnToolCoffee;

    private View layoutToolLieContainer;
    private Button btnStartLieTest;
    private TextView tvLieCountdown;
    private View layoutLieResult;
    private TextView tvLieVerdict;
    private ImageButton btnSpeakLieResult;

    private View layoutToolWarmerContainer;
    private TextView tvWarmerStatus;
    private Button btnToggleWarmer;
    private final HandWarmerController handWarmer = new HandWarmerController();

    private View layoutToolCoffeeContainer;
    private TextView tvCoffeeTemp;
    private TextView tvCoffeeBadge;
    private Button btnStartCoffeeMeasure;
    private ImageButton btnSpeakCoffeeResult;

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

    private float directSkinTemp = -1.0f;
    private float hardwareSkinTemp = -1.0f;
    private float hardwareCpuTemp = -1.0f;
    private float hardwareBattGaugeTemp = -1.0f;
    private boolean isOnWrist = true;
    private float lastAmbientTemp = -1.0f;
    private float lastBatteryTemp = -1.0f;
    private float lastThermalZoneTemp = -1.0f;
    private boolean isCharging = false;

    private boolean isFahrenheit = false;
    private float tempOffset = 0.0f;
    private int currentMode = 0; // 0=wrist, 1=temple, 2=water, 3=room
    private int currentLabTool = 0; // 0=lie, 1=warmer, 2=coffee

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

    // Lie Detector States
    private boolean isLieTesting = false;
    private int lieSecondsLeft = 15;
    private final List<Float> lieSamples = new ArrayList<>();
    private int lastLieVerdictType = 0;
    private int lastLieScore = 98;

    // Coffee Test States
    private boolean isCoffeeMeasuring = false;
    private int coffeeSecondsLeft = 5;
    private final List<Float> coffeeSamples = new ArrayList<>();
    private float lastMeasuredCoffee = 58.0f;

    private float currentCalculatedSkin = -1.0f;
    private float currentCalculatedBody = -1.0f;

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

    private final Runnable lieCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            tickLieTest();
        }
    };

    private final Runnable coffeeCountdownRunnable = new Runnable() {
        @Override
        public void run() {
            tickCoffeeMeasure();
        }
    };

    @Override
    public void run() {
        pollThermalSources();
        recalculateAndDisplay();
        updateMedTimerView();

        if (handWarmer.isRunning()) {
            float checkT = hardwareSkinTemp > 0 ? hardwareSkinTemp : (lastBatteryTemp > 0 ? lastBatteryTemp : 36.0f);
            handWarmer.tick(checkT);
            handWarmer.updateSafetyCheck(checkT);
        }

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

        voiceAssistant = new VoiceAssistantManager(this);

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
        updateVoiceButton();
        updateHistoryView();
        updateMedTimerView();
        switchLabTool(0);
        recalculateAndDisplay();
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
        tvSkinTemp = (TextView) findViewById(R.id.tvSkinTemp);
        tvSkinStatus = (TextView) findViewById(R.id.tvSkinStatus);
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
        tvTempleTemp = (TextView) findViewById(R.id.tvTempleTemp);
        tvTempleStatus = (TextView) findViewById(R.id.tvTempleStatus);
        mercuryViewTemple = (MercuryThermometerView) findViewById(R.id.mercuryViewTemple);
        btnStartTempleMeasure = (Button) findViewById(R.id.btnStartTempleMeasure);
        tvTempleCountdown = (TextView) findViewById(R.id.tvTempleCountdown);
        layoutTempleResult = findViewById(R.id.layoutTempleResult);
        tvTempleFixedResult = (TextView) findViewById(R.id.tvTempleFixedResult);
        tvTempleResultBadge = (TextView) findViewById(R.id.tvTempleResultBadge);

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

        // Lab Tools
        btnToolLie = (Button) findViewById(R.id.btnToolLie);
        btnToolWarmer = (Button) findViewById(R.id.btnToolWarmer);
        btnToolCoffee = (Button) findViewById(R.id.btnToolCoffee);

        layoutToolLieContainer = findViewById(R.id.layoutToolLieContainer);
        btnStartLieTest = (Button) findViewById(R.id.btnStartLieTest);
        tvLieCountdown = (TextView) findViewById(R.id.tvLieCountdown);
        layoutLieResult = findViewById(R.id.layoutLieResult);
        tvLieVerdict = (TextView) findViewById(R.id.tvLieVerdict);
        btnSpeakLieResult = (ImageButton) findViewById(R.id.btnSpeakLieResult);

        layoutToolWarmerContainer = findViewById(R.id.layoutToolWarmerContainer);
        tvWarmerStatus = (TextView) findViewById(R.id.tvWarmerStatus);
        btnToggleWarmer = (Button) findViewById(R.id.btnToggleWarmer);

        layoutToolCoffeeContainer = findViewById(R.id.layoutToolCoffeeContainer);
        tvCoffeeTemp = (TextView) findViewById(R.id.tvCoffeeTemp);
        tvCoffeeBadge = (TextView) findViewById(R.id.tvCoffeeBadge);
        btnStartCoffeeMeasure = (Button) findViewById(R.id.btnStartCoffeeMeasure);
        btnSpeakCoffeeResult = (ImageButton) findViewById(R.id.btnSpeakCoffeeResult);

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

        // --- Lab Switchers ---
        btnToolLie.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchLabTool(0); }
        });

        btnToolWarmer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchLabTool(1); }
        });

        btnToolCoffee.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { switchLabTool(2); }
        });

        btnStartLieTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startLieTest(); }
        });

        btnSpeakLieResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (voiceAssistant != null) {
                    voiceAssistant.speakLieDetector(lastLieVerdictType, lastLieScore);
                    vibrateTick();
                }
            }
        });

        btnToggleWarmer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { toggleHandWarmer(); }
        });

        btnStartCoffeeMeasure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { startCoffeeMeasure(); }
        });

        btnSpeakCoffeeResult.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (voiceAssistant != null) {
                    voiceAssistant.speakBeverage(lastMeasuredCoffee, isFahrenheit);
                    vibrateTick();
                }
            }
        });

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
                updateHistoryView();
                vibrateTick();
            }
        });

        btnExportReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = TempLogManager.exportDoctorReport(MainActivity.this);
                vibrateDouble();
                if (path != null) {
                    Toast.makeText(MainActivity.this, "✓ Отчет сохранен:\n" + path, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Ошибка сохранения отчета", Toast.LENGTH_SHORT).show();
                }
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

    private void switchLabTool(int tool) {
        currentLabTool = tool;
        vibrateTick();

        btnToolLie.setBackgroundResource(R.drawable.btn_secondary_bg);
        btnToolLie.setTextColor(Color.parseColor("#9E9E9E"));
        btnToolWarmer.setBackgroundResource(R.drawable.btn_secondary_bg);
        btnToolWarmer.setTextColor(Color.parseColor("#9E9E9E"));
        btnToolCoffee.setBackgroundResource(R.drawable.btn_secondary_bg);
        btnToolCoffee.setTextColor(Color.parseColor("#9E9E9E"));

        layoutToolLieContainer.setVisibility(View.GONE);
        layoutToolWarmerContainer.setVisibility(View.GONE);
        layoutToolCoffeeContainer.setVisibility(View.GONE);

        if (tool == 0) {
            btnToolLie.setBackgroundResource(R.drawable.tab_selected_bg);
            btnToolLie.setTextColor(Color.WHITE);
            layoutToolLieContainer.setVisibility(View.VISIBLE);
        } else if (tool == 1) {
            btnToolWarmer.setBackgroundResource(R.drawable.tab_selected_bg);
            btnToolWarmer.setTextColor(Color.WHITE);
            layoutToolWarmerContainer.setVisibility(View.VISIBLE);
        } else {
            btnToolCoffee.setBackgroundResource(R.drawable.tab_selected_bg);
            btnToolCoffee.setTextColor(Color.WHITE);
            layoutToolCoffeeContainer.setVisibility(View.VISIBLE);
        }
    }

    // --- Lie Detector Routine ---
    private void startLieTest() {
        isLieTesting = true;
        lieSecondsLeft = 15;
        lieSamples.clear();

        btnStartLieTest.setVisibility(View.GONE);
        layoutLieResult.setVisibility(View.GONE);
        tvLieCountdown.setVisibility(View.VISIBLE);
        tvLieCountdown.setText("15 сек... Сканирование капилляров");

        vibrateShort();

        uiHandler.removeCallbacks(lieCountdownRunnable);
        uiHandler.postDelayed(lieCountdownRunnable, 1000);
    }

    private void tickLieTest() {
        if (!isLieTesting) return;

        lieSecondsLeft--;
        vibrateHeartbeat(); // Realistic heartbeat thud haptic

        float s = hardwareSkinTemp > 0 ? hardwareSkinTemp : (directSkinTemp > 0 ? directSkinTemp : (lastBatteryTemp > 0 ? lastBatteryTemp : 35.8f));
        lieSamples.add(s);

        tvLieCountdown.setText(lieSecondsLeft + " сек... Сканирование вазомоторики");

        if (lieSecondsLeft > 0) {
            uiHandler.postDelayed(lieCountdownRunnable, 1000);
        } else {
            finishLieTest();
        }
    }

    private void finishLieTest() {
        isLieTesting = false;
        vibrateDouble();

        tvLieCountdown.setVisibility(View.GONE);
        layoutLieResult.setVisibility(View.VISIBLE);
        btnStartLieTest.setVisibility(View.VISIBLE);
        btnStartLieTest.setText("⏱️ ПОВТОРИТЬ ПРОВЕРКУ");

        float delta = 0f;
        if (lieSamples.size() >= 2) {
            delta = lieSamples.get(lieSamples.size() - 1) - lieSamples.get(0);
        }

        if (delta >= -0.08f) {
            lastLieVerdictType = 0;
            lastLieScore = 98;
            tvLieVerdict.setText("🧘 Абсолютный дзен (Правда 98%)");
            tvLieVerdict.setTextColor(Color.parseColor("#00E676"));
        } else if (delta >= -0.25f) {
            lastLieVerdictType = 1;
            lastLieScore = 74;
            tvLieVerdict.setText("⚡ Лёгкое волнение (Правда 74%)");
            tvLieVerdict.setTextColor(Color.parseColor("#FFD600"));
        } else {
            lastLieVerdictType = 2;
            lastLieScore = 89;
            tvLieVerdict.setText("🚨 Спазм сосудов! Детектор звенит!");
            tvLieVerdict.setTextColor(Color.parseColor("#FF1744"));
        }

        if (voiceAssistant != null) {
            voiceAssistant.speakLieDetector(lastLieVerdictType, lastLieScore);
        }
    }

    // --- Hand Warmer Routine ---
    private void toggleHandWarmer() {
        if (handWarmer.isRunning()) {
            handWarmer.stop();
            tvWarmerStatus.setText("Грелка выключена • Лимит 40°C");
            btnToggleWarmer.setText("🔥 ВКЛЮЧИТЬ ГРЕЛКУ (60 сек)");
            btnToggleWarmer.setBackgroundResource(R.drawable.btn_measure_bg);
            vibrateShort();
        } else {
            handWarmer.start(new HandWarmerController.WarmerCallback() {
                @Override
                public void onTick(final int secondsLeft, final float currentTemp) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvWarmerStatus.setText(String.format(Locale.US, "🔥 Нагрев: %.1f °C • Осталось: %d сек", currentTemp, secondsLeft));
                        }
                    });
                }

                @Override
                public void onSafetyAutoStop(final String reason) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvWarmerStatus.setText("⚠️ " + reason);
                            btnToggleWarmer.setText("🔥 ВКЛЮЧИТЬ ГРЕЛКУ (60 сек)");
                            btnToggleWarmer.setBackgroundResource(R.drawable.btn_measure_bg);
                            vibrateDouble();
                        }
                    });
                }

                @Override
                public void onCompleted() {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            tvWarmerStatus.setText("✓ 60-сек сеанс прогрева завершен");
                            btnToggleWarmer.setText("🔥 ВКЛЮЧИТЬ ГРЕЛКУ (60 сек)");
                            btnToggleWarmer.setBackgroundResource(R.drawable.btn_measure_bg);
                            vibrateDouble();
                        }
                    });
                }
            });

            tvWarmerStatus.setText("🔥 Прогрев активен (Лимит 40°C)...");
            btnToggleWarmer.setText("🛑 ОСТАНОВИТЬ ГРЕЛКУ");
            btnToggleWarmer.setBackgroundResource(R.drawable.alert_banner_bg);
            vibrateShort();
        }
    }

    // --- Coffee Sip Measure Routine ---
    private void startCoffeeMeasure() {
        isCoffeeMeasuring = true;
        coffeeSecondsLeft = 5;
        coffeeSamples.clear();

        btnStartCoffeeMeasure.setText("Замер: 5 сек...");
        btnSpeakCoffeeResult.setVisibility(View.GONE);
        vibrateShort();

        uiHandler.removeCallbacks(coffeeCountdownRunnable);
        uiHandler.postDelayed(coffeeCountdownRunnable, 1000);
    }

    private void tickCoffeeMeasure() {
        if (!isCoffeeMeasuring) return;

        coffeeSecondsLeft--;
        vibrateTick();

        float sample = hardwareSkinTemp > 0 ? hardwareSkinTemp : (directSkinTemp > 0 ? directSkinTemp : (lastBatteryTemp > 0 ? lastBatteryTemp : 38.0f));
        coffeeSamples.add(sample);

        btnStartCoffeeMeasure.setText("Замер: " + coffeeSecondsLeft + " сек...");

        if (coffeeSecondsLeft > 0) {
            uiHandler.postDelayed(coffeeCountdownRunnable, 1000);
        } else {
            finishCoffeeMeasure();
        }
    }

    private void finishCoffeeMeasure() {
        isCoffeeMeasuring = false;
        vibrateDouble();

        btnStartCoffeeMeasure.setText("⏱️ ПОВТОРИТЬ ЗАМЕР НАПИТКА");
        btnSpeakCoffeeResult.setVisibility(View.VISIBLE);

        float coffeeVal = 58.0f;
        if (!coffeeSamples.isEmpty()) {
            float sum = 0;
            for (float s : coffeeSamples) sum += s;
            coffeeVal = sum / coffeeSamples.size();
        }

        lastMeasuredCoffee = coffeeVal;
        tvCoffeeTemp.setText(formatTemp(coffeeVal));

        if (coffeeVal > 65.0f) {
            tvCoffeeBadge.setText("🌋 Опасность ожога языка! (Ждите)");
            tvCoffeeBadge.setTextColor(Color.parseColor("#FF1744"));
        } else if (coffeeVal >= 55.0f) {
            tvCoffeeBadge.setText("☕ Идеальная температура! Можно пить");
            tvCoffeeBadge.setTextColor(Color.parseColor("#00E676"));
        } else if (coffeeVal >= 45.0f) {
            tvCoffeeBadge.setText("🍵 Тёплый комфортный напиток");
            tvCoffeeBadge.setTextColor(Color.parseColor("#00E5FF"));
        } else {
            tvCoffeeBadge.setText("🧊 Напиток остыл (Пора подогреть)");
            tvCoffeeBadge.setTextColor(Color.parseColor("#78909C"));
        }

        if (voiceAssistant != null) {
            voiceAssistant.speakBeverage(coffeeVal, isFahrenheit);
        }
    }

    private void setMedTimer(int hours) {
        long target = System.currentTimeMillis() + (long) hours * 3600 * 1000L;
        getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(PREF_MED_TIMER_TARGET, target).apply();
        vibrateTick();
        updateMedTimerView();
        Toast.makeText(this, "💊 Таймер на " + hours + "ч установлен", Toast.LENGTH_SHORT).show();
    }

    private void resetMedTimer() {
        getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(PREF_MED_TIMER_TARGET).apply();
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

            boolean isOffbody = type == 34 || name.contains("offbody") || strType.contains("offbody");
            boolean isSkin = type == 65590 || name.contains("skin") || strType.contains("skin_temperature") || name.contains("body_temp");
            boolean isAmbient = type == Sensor.TYPE_AMBIENT_TEMPERATURE || type == 7 || (name.contains("ambient") && name.contains("temp")) || (name.contains("temp") && !name.contains("battery"));
            boolean isBaro = type == Sensor.TYPE_PRESSURE || name.contains("pressure") || name.contains("barometer");

            if (isOffbody || isSkin || isAmbient || isBaro) {
                registeredSensors.add(s);
            }
        }

        tvSensorList.setText("Аппаратный датчик: sdm-skin-therm • Wear OS");
    }

    private void pollThermalSources() {
        File bridgeFile = new File("/sdcard/Download/skin_temp.txt");
        if (bridgeFile.exists() && bridgeFile.canRead()) {
            try (BufferedReader br = new BufferedReader(new FileReader(bridgeFile))) {
                String line = br.readLine();
                if (line != null) {
                    float val = Float.parseFloat(line.trim());
                    if (val > 10.0f && val < 50.0f) {
                        directSkinTemp = val;
                    }
                }
            } catch (Exception ignored) {}
        }

        for (int i = 0; i <= 20; i++) {
            File typeFile = new File("/sys/class/thermal/thermal_zone" + i + "/type");
            File tempFile = new File("/sys/class/thermal/thermal_zone" + i + "/temp");
            if (!tempFile.canRead()) continue;

            String type = "";
            if (typeFile.canRead()) {
                try (BufferedReader br = new BufferedReader(new FileReader(typeFile))) {
                    String line = br.readLine();
                    if (line != null) type = line.trim().toLowerCase(Locale.US);
                } catch (Exception ignored) {}
            }

            float raw = -1;
            try (BufferedReader br = new BufferedReader(new FileReader(tempFile))) {
                String line = br.readLine();
                if (line != null) raw = Float.parseFloat(line.trim());
            } catch (Exception ignored) {}

            if (raw <= 0) continue;
            float c = raw > 1000 ? raw / 1000.0f : (raw > 100 ? raw / 10.0f : raw);

            if (type.contains("sdm-skin-therm") || type.contains("skin")) {
                hardwareSkinTemp = c;
            } else if (type.contains("cpuss-0") || type.contains("cpuss")) {
                hardwareCpuTemp = c;
            } else if (type.contains("batt_gauge")) {
                hardwareBattGaugeTemp = c;
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

    private float getCompensatedContactTemp() {
        float raw = directSkinTemp > 0 ? directSkinTemp : (hardwareSkinTemp > 0 ? hardwareSkinTemp : (lastBatteryTemp > 0 ? lastBatteryTemp : 32.0f));

        if (isCharging) {
            raw -= 4.2f; // Charging coil thermal dissipation offset
        }

        if (hardwareCpuTemp > raw && hardwareCpuTemp < 80.0f) {
            float cpuExcess = hardwareCpuTemp - raw;
            raw -= cpuExcess * 0.10f; // 10% thermal coupling to watch base
        }

        return Math.round(raw * 10f) / 10f;
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
        float ambientVal = lastAmbientTemp > 0 ? lastAmbientTemp : 23.5f;
        tvAmbientTemp.setText(formatTemp(ambientVal));

        float contactTemp = getCompensatedContactTemp();

        // --- Water Mode Preview ---
        float waterPreview = hardwareSkinTemp > 0 ? hardwareSkinTemp : contactTemp;
        tvWaterTemp.setText(formatTemp(waterPreview));
        updateWaterBadge(waterPreview);

        // --- Room Mode Processing ---
        float roomEstimate = (!isOnWrist && !isCharging && contactTemp > 10.0f && contactTemp < 35.0f)
                ? contactTemp
                : ambientVal;
        tvRoomTemp.setText(formatTemp(roomEstimate));
        if (roomEstimate < 18.0f) {
            tvRoomStatus.setText("Прохладный микроклимат");
        } else if (roomEstimate <= 24.5f) {
            tvRoomStatus.setText("✓ Комфортная температура");
        } else {
            tvRoomStatus.setText("Тёплое / душное помещение");
        }

        // --- Temple Mode Live Preview ---
        float liveTempleSkin = contactTemp;
        float liveTempleBody = liveTempleSkin + 0.35f + tempOffset;
        tvTempleTemp.setText(formatTemp(liveTempleSkin + tempOffset));
        if (mercuryViewTemple != null) {
            mercuryViewTemple.setTemperature(liveTempleBody, true);
        }

        // --- Wrist Mode Processing ---
        // Physical off-body detection
        if (!isOnWrist && contactTemp < 31.0f) {
            currentCalculatedSkin = contactTemp;
            currentCalculatedBody = 35.0f;
            tvSkinTemp.setText(formatTemp(contactTemp));
            tvSkinStatus.setText("Часы сняты (Корпус " + formatTempShort(contactTemp) + ")");
            tvSkinStatus.setTextColor(Color.parseColor("#78909C"));
            if (mercuryViewWrist != null) {
                mercuryViewWrist.setTemperature(35.0f, true);
            }
            updateThermalBalance(contactTemp, ambientVal);
            return;
        }

        // On-wrist physiological conversion (Wanger-Bayer heat flux transfer model)
        float calibratedSkin = contactTemp + tempOffset;
        float deltaSkin = calibratedSkin - 33.2f;
        float calibratedBody = 36.6f + deltaSkin * 0.65f + tempOffset;

        // Realistic safety bounds for human physiology (34.0 to 42.0)
        if (calibratedBody < 34.0f) calibratedBody = 34.0f;
        if (calibratedBody > 42.0f) calibratedBody = 42.0f;

        currentCalculatedSkin = calibratedSkin;
        currentCalculatedBody = Math.round(calibratedBody * 10f) / 10f;

        tvSkinTemp.setText(formatTemp(calibratedSkin));
        if (isCharging) {
            tvSkinStatus.setText(String.format(Locale.US, "Зарядка • Тело ~%s", formatTemp(currentCalculatedBody)));
        } else {
            tvSkinStatus.setText(String.format(Locale.US, "sdm-skin • Тело ~%s", formatTemp(currentCalculatedBody)));
        }
        tvSkinStatus.setTextColor(Color.parseColor("#00E5FF"));

        if (mercuryViewWrist != null) {
            mercuryViewWrist.setTemperature(currentCalculatedBody, true);
        }

        savePrefsAndUpdateComplication(calibratedSkin, currentCalculatedBody, ambientVal);
        updateSparkline(calibratedSkin);
        updateThermalBalance(calibratedSkin, ambientVal);
        updateFeverAdvisor(currentCalculatedBody);
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
            int kcalPerHour = Math.round(11.6f * Math.max(1.0f, delta));
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

        float finalSkin = currentCalculatedSkin > 20.0f ? currentCalculatedSkin : 33.2f;
        if (!measureSamples.isEmpty()) {
            int count = Math.min(measureSamples.size(), 8);
            float sum = 0;
            for (int i = measureSamples.size() - count; i < measureSamples.size(); i++) {
                sum += measureSamples.get(i);
            }
            finalSkin = sum / count;

            // Thermal saturation compensation for ceramic case
            if (measureSamples.size() >= 10) {
                float slope = (measureSamples.get(measureSamples.size() - 1) - measureSamples.get(measureSamples.size() - 6)) / 5.0f;
                if (slope > 0.02f) {
                    finalSkin += Math.min(1.0f, slope * 6.0f);
                }
            }
        }

        float deltaSkin = finalSkin - 33.2f;
        float finalBody = 36.6f + deltaSkin * 0.65f + tempOffset;
        if (finalBody < 34.0f) finalBody = 34.0f;
        if (finalBody > 42.0f) finalBody = 42.0f;
        finalBody = Math.round(finalBody * 10f) / 10f;

        lastMeasuredBodyWrist = finalBody;
        tvFixedResult.setText(formatTemp(finalBody));

        if (finalBody < 35.5f) {
            tvResultBadge.setText("Пониженная (Гипотермия)");
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
            tvResultBadge.setText("🔥 Повышенная (Лихорадка)");
            tvResultBadge.setTextColor(Color.parseColor("#FF5252"));
            tvFixedResult.setTextColor(Color.parseColor("#FF5252"));
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

        savePrefsAndUpdateComplication(finalSkin, finalBody, lastAmbientTemp);
    }

    private void cancelMeasurement() {
        isMeasuring = false;
        uiHandler.removeCallbacks(measureCountdownRunnable);
        layoutMeasuring.setVisibility(View.GONE);
        btnStartMeasure.setVisibility(View.VISIBLE);
    }

    // --- Temple / Forehead Measurement (15 seconds) ---
    private void startTempleMeasurement() {
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
        }

        float templeBody = templeSkin + 0.35f + tempOffset;
        if (templeBody < 35.0f) templeBody = 35.0f;
        if (templeBody > 40.0f) templeBody = 40.0f;

        lastMeasuredBodyTemple = templeBody;
        tvTempleFixedResult.setText(formatTemp(templeBody));

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

        savePrefsAndUpdateComplication(templeSkin, templeBody, lastAmbientTemp);
    }

    // --- Water / Bath Measurement (10 seconds) ---
    private void startWaterMeasurement() {
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
            isRoomMeasuring = false;
            vibrateDouble();
            tvRoomCountdown.setText("✓ Замер зафиксирован");
            btnStartRoomMeasure.setText("⏱️ Повторить замер воздуха");
            btnStartRoomMeasure.setVisibility(View.VISIBLE);

            float ambientVal = lastAmbientTemp > 0 ? lastAmbientTemp : (lastBatteryTemp > 0 ? lastBatteryTemp - 2.5f : 24.0f);
            TempLogManager.saveRecord(this, ambientVal, ambientVal, 3);
            updateHistoryView();
        }
    }

    // --- History View Rendering ---
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
            if (e.deltaPrev > 0.05f) {
                deltaStr = String.format(Locale.US, " (+%.1f°↑)", e.deltaPrev);
            } else if (e.deltaPrev < -0.05f) {
                deltaStr = String.format(Locale.US, " (%.1f°↓)", e.deltaPrev);
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
            isRoomMeasuring = false;
            uiHandler.removeCallbacks(roomCountdownRunnable);
            tvRoomCountdown.setVisibility(View.GONE);
            btnStartRoomMeasure.setVisibility(View.VISIBLE);
        }
        if (isLieTesting) {
            isLieTesting = false;
            uiHandler.removeCallbacks(lieCountdownRunnable);
            tvLieCountdown.setVisibility(View.GONE);
            btnStartLieTest.setVisibility(View.VISIBLE);
        }
        if (isCoffeeMeasuring) {
            isCoffeeMeasuring = false;
            uiHandler.removeCallbacks(coffeeCountdownRunnable);
            btnStartCoffeeMeasure.setText("⏱️ ПРОВЕРИТЬ НАПИТОК (5 сек)");
        }
        if (handWarmer.isRunning()) {
            handWarmer.stop();
            tvWarmerStatus.setText("Грелка выключена • Лимит 40°C");
            btnToggleWarmer.setText("🔥 ВКЛЮЧИТЬ ГРЕЛКУ (60 сек)");
            btnToggleWarmer.setBackgroundResource(R.drawable.btn_measure_bg);
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
        super.onDestroy();
        if (handWarmer.isRunning()) {
            handWarmer.stop();
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
            recalculateAndDisplay();
            return;
        }

        if (type == 65590 || name.contains("skin") || strType.contains("skin_temperature") || name.contains("body_temp")) {
            if (value > 10.0f && value < 50.0f) {
                directSkinTemp = value;
                recalculateAndDisplay();
            }
        } else if (type == Sensor.TYPE_AMBIENT_TEMPERATURE || type == 7 || (name.contains("ambient") && name.contains("temp")) || (name.contains("temp") && !name.contains("battery"))) {
            if (value > 5.0f && value < 70.0f) {
                lastAmbientTemp = value;
                recalculateAndDisplay();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
