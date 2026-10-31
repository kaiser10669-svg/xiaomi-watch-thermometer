package com.kaiserdev.watchthermometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Build;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class VoiceAssistantManager implements TextToSpeech.OnInitListener {

    private static final String PREF_VOICE_MUTED = "voice_muted";
    private final Context appContext;
    private TextToSpeech tts;
    private boolean isReady = false;
    private boolean isMuted = false;
    private boolean fallbackAttempted = false;

    public VoiceAssistantManager(Context context) {
        this.appContext = context.getApplicationContext();
        SharedPreferences sp = appContext.getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE);
        this.isMuted = sp.getBoolean(PREF_VOICE_MUTED, false);

        initTtsEngine();
    }

    private void initTtsEngine() {
        try {
            // First attempt: explicitly target Google TTS engine on Wear OS
            tts = new TextToSpeech(appContext, this, "com.google.android.tts");
        } catch (Throwable t) {
            try {
                fallbackAttempted = true;
                tts = new TextToSpeech(appContext, this);
            } catch (Throwable ignored) {
                isReady = false;
            }
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            Locale ruLocale = new Locale("ru", "RU");
            int res = tts.setLanguage(ruLocale);
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
            tts.setSpeechRate(0.92f); // Clear, articulate cadence for watch speaker
            tts.setPitch(1.05f);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(attrs);
            }
            isReady = true;
        } else {
            if (!fallbackAttempted) {
                fallbackAttempted = true;
                try {
                    if (tts != null) {
                        tts.shutdown();
                    }
                } catch (Throwable ignored) {}
                try {
                    // Fallback to system default TTS engine (e.g. Xiaomi Xiao AI on HyperOS)
                    tts = new TextToSpeech(appContext, this);
                    return;
                } catch (Throwable ignored) {}
            }
            isReady = false;
        }
    }

    public void speakTemperature(float tempC, boolean isFahrenheit) {
        if (!isReady || isMuted || tts == null) return;

        String statusPhrase;
        if (tempC < 35.5f) {
            statusPhrase = "Пониженная.";
        } else if (tempC <= 37.2f) {
            statusPhrase = "В пределах нормы!";
        } else if (tempC <= 38.0f) {
            statusPhrase = "Субфебрильная. Будьте внимательны.";
        } else if (tempC <= 38.5f) {
            statusPhrase = "Повышенная! Рекомендуется отдых и обильное питьё.";
        } else {
            statusPhrase = "Внимание! Лихорадка! Примите жаропонижающее.";
        }

        String phrase;
        if (isFahrenheit) {
            float f = tempC * 1.8f + 32f;
            int whole = (int) f;
            int decimal = Math.round((f - whole) * 10f);
            if (decimal == 10) { whole++; decimal = 0; }
            if (decimal == 0) {
                phrase = String.format(Locale.US, "Температура %d градусов Фаренгейта. %s", whole, statusPhrase);
            } else {
                phrase = String.format(Locale.US, "Температура %d и %d градусов Фаренгейта. %s", whole, decimal, statusPhrase);
            }
        } else {
            int whole = (int) tempC;
            int decimal = Math.round((tempC - whole) * 10f);
            if (decimal == 10) { whole++; decimal = 0; }

            if (decimal == 0) {
                phrase = String.format(Locale.US, "Температура %d ровно. %s", whole, statusPhrase);
            } else {
                phrase = String.format(Locale.US, "Температура %d и %d. %s", whole, decimal, statusPhrase);
            }
        }

        speak(phrase);
    }

    public void speakRoom(float tempC, boolean isFahrenheit) {
        if (!isReady || isMuted || tts == null) return;

        String advice;
        if (tempC < 18.0f) {
            advice = "Прохладный микроклимат в помещении.";
        } else if (tempC <= 24.5f) {
            advice = "Комфортная температура воздуха.";
        } else {
            advice = "Тепло и душно. Рекомендуется проветрить комнату.";
        }

        String phrase;
        if (isFahrenheit) {
            float f = tempC * 1.8f + 32f;
            int whole = (int) f;
            int decimal = Math.round((f - whole) * 10f);
            if (decimal == 10) { whole++; decimal = 0; }
            phrase = String.format(Locale.US, "Температура воздуха %d и %d градусов Фаренгейта. %s", whole, decimal, advice);
        } else {
            int whole = (int) tempC;
            int decimal = Math.round((tempC - whole) * 10f);
            if (decimal == 10) { whole++; decimal = 0; }

            if (decimal == 0) {
                phrase = String.format(Locale.US, "Температура воздуха %d градусов. %s", whole, advice);
            } else {
                phrase = String.format(Locale.US, "Температура воздуха %d и %d. %s", whole, decimal, advice);
            }
        }

        speak(phrase);
    }

    public void speakWater(float tempC, boolean isFahrenheit) {
        if (!isReady || isMuted || tts == null) return;

        String advice;
        if (tempC < 15.0f) {
            advice = "Ледяная купель для моржевания.";
        } else if (tempC <= 28.5f) {
            advice = "Бодрящая вода для плавания.";
        } else if (tempC <= 35.9f) {
            advice = "Приятная теплая вода.";
        } else if (tempC <= 38.5f) {
            advice = "Идеально для купания ребенка!";
        } else if (tempC <= 41.5f) {
            advice = "Горячая расслабляющая ванна.";
        } else {
            advice = "Внимание! Слишком горячо! Опасность ожога!";
        }

        String phrase;
        if (isFahrenheit) {
            float f = tempC * 1.8f + 32f;
            int whole = (int) f;
            int decimal = Math.round((f - whole) * 10f);
            if (decimal == 10) { whole++; decimal = 0; }
            phrase = String.format(Locale.US, "Температура воды %d и %d градусов Фаренгейта. %s", whole, decimal, advice);
        } else {
            int whole = (int) tempC;
            int decimal = Math.round((tempC - whole) * 10f);
            if (decimal == 10) { whole++; decimal = 0; }

            if (decimal == 0) {
                phrase = String.format(Locale.US, "Температура воды %d градусов. %s", whole, advice);
            } else {
                phrase = String.format(Locale.US, "Температура воды %d и %d. %s", whole, decimal, advice);
            }
        }

        speak(phrase);
    }

    private void speak(String text) {
        if (tts == null || !isReady) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "THERMOMETER_ANNOUNCE");
            } else {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        } catch (Throwable ignored) {}
    }

    public void stop() {
        if (tts != null && isReady) {
            try {
                tts.stop();
            } catch (Throwable ignored) {}
        }
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
        appContext.getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_VOICE_MUTED, muted).apply();
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void toggleMute() {
        setMuted(!this.isMuted);
    }

    public boolean isReady() {
        return isReady;
    }

    public void destroy() {
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Throwable ignored) {}
            tts = null;
        }
        isReady = false;
    }
}
