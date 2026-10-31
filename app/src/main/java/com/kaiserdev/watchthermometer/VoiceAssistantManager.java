package com.kaiserdev.watchthermometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

public class VoiceAssistantManager implements TextToSpeech.OnInitListener {

    private static final String PREF_VOICE_MUTED = "voice_muted";
    private static final String UTTERANCE_ID = "THERMOMETER_ANNOUNCE";

    private final Context appContext;
    private TextToSpeech tts;
    private AudioManager audioManager;
    private AudioAttributes audioAttributes;
    private Object focusRequestObj; // AudioFocusRequest on API 26+

    private boolean isReady = false;
    private boolean isMuted = false;
    private boolean fallbackAttempted = false;

    public VoiceAssistantManager(Context context) {
        this.appContext = context.getApplicationContext();
        SharedPreferences sp = appContext.getSharedPreferences(ThermometerComplicationService.PREFS_NAME, Context.MODE_PRIVATE);
        this.isMuted = sp.getBoolean(PREF_VOICE_MUTED, false);
        this.audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
        }

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && audioAttributes != null) {
                tts.setAudioAttributes(audioAttributes);
            }

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {}

                @Override
                public void onDone(String utteranceId) {
                    abandonFocus();
                }

                @Override
                public void onError(String utteranceId) {
                    abandonFocus();
                }
            });

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

    private void requestFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioAttributes != null) {
                AudioFocusRequest afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(audioAttributes)
                        .build();
                focusRequestObj = afr;
                audioManager.requestAudioFocus(afr);
            } else {
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            }
        } catch (Throwable ignored) {}
    }

    private void abandonFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequestObj instanceof AudioFocusRequest) {
                audioManager.abandonAudioFocusRequest((AudioFocusRequest) focusRequestObj);
                focusRequestObj = null;
            } else {
                audioManager.abandonAudioFocus(null);
            }
        } catch (Throwable ignored) {}
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


    private void speak(String text) {
        if (tts == null || !isReady) return;
        requestFocus();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
            } else {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
        } catch (Throwable ignored) {
            abandonFocus();
        }
    }

    public void stop() {
        if (tts != null && isReady) {
            try {
                tts.stop();
            } catch (Throwable ignored) {}
        }
        abandonFocus();
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
        abandonFocus();
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
