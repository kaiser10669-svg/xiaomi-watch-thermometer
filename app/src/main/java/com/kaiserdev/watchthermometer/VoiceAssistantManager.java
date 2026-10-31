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

    private boolean isRussianLocale() {
        if (tts == null) return true;
        try {
            Locale cur = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cur = tts.getVoice() != null ? tts.getVoice().getLocale() : null;
            }
            if (cur == null) {
                cur = tts.getLanguage();
            }
            if (cur != null && cur.getLanguage() != null) {
                return "ru".equalsIgnoreCase(cur.getLanguage());
            }
        } catch (Throwable ignored) {}
        return true;
    }

    public void speakTemperature(float tempC, boolean isFahrenheit) {
        speakTemperature(tempC, isFahrenheit, false);
    }

    public void speakTemperature(float tempC, boolean isFahrenheit, boolean force) {
        if (!isReady || (!force && isMuted) || tts == null) return;

        boolean isRu = isRussianLocale();
        String phrase;
        if (isRu) {
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

            if (isFahrenheit) {
                float f = tempC * 1.8f + 32f;
                int whole = (int) f;
                int decimal = Math.round((f - whole) * 10f);
                if (decimal == 10) { whole++; decimal = 0; }
                if (decimal == 0) {
                    phrase = String.format(Locale.US, "Температура %d %s по Фаренгейту. %s", whole, getDegreesWord(whole), statusPhrase);
                } else {
                    phrase = String.format(Locale.US, "Температура %d и %d по Фаренгейту. %s", whole, decimal, statusPhrase);
                }
            } else {
                int whole = (int) tempC;
                int decimal = Math.round((tempC - whole) * 10f);
                if (decimal == 10) { whole++; decimal = 0; }

                if (decimal == 0) {
                    phrase = String.format(Locale.US, "Температура %d %s ровно. %s", whole, getDegreesWord(whole), statusPhrase);
                } else {
                    phrase = String.format(Locale.US, "Температура %d и %d. %s", whole, decimal, statusPhrase);
                }
            }
        } else {
            String enStatus;
            if (tempC < 35.5f) {
                enStatus = "Low body temperature.";
            } else if (tempC <= 37.2f) {
                enStatus = "Normal temperature.";
            } else if (tempC <= 38.0f) {
                enStatus = "Mild elevation.";
            } else if (tempC <= 38.5f) {
                enStatus = "Moderate fever. Rest and drink fluids.";
            } else {
                enStatus = "High fever! Please consult a physician.";
            }

            if (isFahrenheit) {
                float f = tempC * 1.8f + 32f;
                phrase = String.format(Locale.US, "Body temperature %.1f degrees Fahrenheit. %s", f, enStatus);
            } else {
                phrase = String.format(Locale.US, "Body temperature %.1f degrees Celsius. %s", tempC, enStatus);
            }
        }

        speak(phrase);
    }

    public void speakRoom(float tempC, boolean isFahrenheit) {
        speakRoom(tempC, isFahrenheit, false);
    }

    public void speakRoom(float tempC, boolean isFahrenheit, boolean force) {
        if (!isReady || (!force && isMuted) || tts == null) return;

        boolean isRu = isRussianLocale();
        String phrase;
        if (isRu) {
            String advice;
            if (tempC < 18.0f) {
                advice = "Прохладный микроклимат в помещении.";
            } else if (tempC <= 24.5f) {
                advice = "Комфортная температура воздуха.";
            } else {
                advice = "Тепло и душно. Рекомендуется проветрить комнату.";
            }

            if (isFahrenheit) {
                float f = tempC * 1.8f + 32f;
                int whole = (int) f;
                int decimal = Math.round((f - whole) * 10f);
                if (decimal == 10) { whole++; decimal = 0; }
                if (decimal == 0) {
                    phrase = String.format(Locale.US, "Температура воздуха %d %s по Фаренгейту. %s", whole, getDegreesWord(whole), advice);
                } else {
                    phrase = String.format(Locale.US, "Температура воздуха %d и %d по Фаренгейту. %s", whole, decimal, advice);
                }
            } else {
                int whole = (int) tempC;
                int decimal = Math.round((tempC - whole) * 10f);
                if (decimal == 10) { whole++; decimal = 0; }

                if (decimal == 0) {
                    phrase = String.format(Locale.US, "Температура воздуха %d %s. %s", whole, getDegreesWord(whole), advice);
                } else {
                    phrase = String.format(Locale.US, "Температура воздуха %d и %d. %s", whole, decimal, advice);
                }
            }
        } else {
            String enAdvice;
            if (tempC < 18.0f) {
                enAdvice = "Cool room climate.";
            } else if (tempC <= 24.5f) {
                enAdvice = "Comfortable room temperature.";
            } else {
                enAdvice = "Warm and stuffy. Consider airing out.";
            }

            if (isFahrenheit) {
                float f = tempC * 1.8f + 32f;
                phrase = String.format(Locale.US, "Room temperature %.1f degrees Fahrenheit. %s", f, enAdvice);
            } else {
                phrase = String.format(Locale.US, "Room temperature %.1f degrees Celsius. %s", tempC, enAdvice);
            }
        }

        speak(phrase);
    }


    private static String getDegreesWord(int n) {
        int abs = Math.abs(n);
        int rem100 = abs % 100;
        int rem10 = abs % 10;
        if (rem100 >= 11 && rem100 <= 19) return "градусов";
        if (rem10 == 1) return "градус";
        if (rem10 >= 2 && rem10 <= 4) return "градуса";
        return "градусов";
    }

    private void speak(String text) {
        if (tts == null || !isReady) return;
        requestFocus();
        try {
            int res;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
            } else {
                res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
            if (res != TextToSpeech.SUCCESS) {
                abandonFocus();
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
