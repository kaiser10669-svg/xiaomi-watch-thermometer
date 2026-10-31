package com.kaiserdev.watchthermometer;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class VoiceAssistantManager implements TextToSpeech.OnInitListener {

    private TextToSpeech tts;
    private boolean isReady = false;
    private boolean isMuted = false;

    public VoiceAssistantManager(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            Locale ruLocale = new Locale("ru", "RU");
            int res = tts.setLanguage(ruLocale);
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
            tts.setSpeechRate(0.92f); // Мягкий и разборчивый темп для динамика часов
            tts.setPitch(1.05f);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                tts.setAudioAttributes(attrs);
            }
            isReady = true;
        }
    }

    public void speakTemperature(float tempC, boolean isFahrenheit) {
        if (!isReady || isMuted || tts == null) return;

        String phrase;
        if (isFahrenheit) {
            float f = tempC * 1.8f + 32f;
            int whole = (int) f;
            int decimal = Math.round((f - whole) * 10f);
            if (decimal == 10) { whole++; decimal = 0; }
            phrase = String.format(Locale.US, "Температура %d и %d градусов Фаренгейта", whole, decimal);
        } else {
            int whole = (int) tempC;
            int decimal = Math.round((tempC - whole) * 10f);
            if (decimal == 10) { whole++; decimal = 0; }

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

            if (decimal == 0) {
                phrase = String.format(Locale.US, "Температура %d ровно. %s", whole, statusPhrase);
            } else {
                phrase = String.format(Locale.US, "Температура %d и %d. %s", whole, decimal, statusPhrase);
            }
        }

        speak(phrase);
    }

    public void speakWater(float tempC, boolean isFahrenheit) {
        if (!isReady || isMuted || tts == null) return;

        int whole = (int) tempC;
        int decimal = Math.round((tempC - whole) * 10f);
        if (decimal == 10) { whole++; decimal = 0; }

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
        if (decimal == 0) {
            phrase = String.format(Locale.US, "Температура воды %d градусов. %s", whole, advice);
        } else {
            phrase = String.format(Locale.US, "Температура воды %d и %d. %s", whole, decimal, advice);
        }

        speak(phrase);
    }

    private void speak(String text) {
        if (tts == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "THERMOMETER_ANNOUNCE");
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void toggleMute() {
        this.isMuted = !this.isMuted;
    }

    public void destroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        isReady = false;
    }
}
