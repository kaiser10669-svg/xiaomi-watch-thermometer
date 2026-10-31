package com.kaiserdev.watchthermometer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TempLogManager {

    private static final String PREFS_NAME = "watch_thermometer_prefs";
    private static final String PREF_HISTORY_KEY = "temp_history_json";
    private static final int MAX_ENTRIES = 16;

    public static class Entry {
        public long timestamp;
        public float tempBody;
        public float tempSkin;
        public int mode; // 0=wrist, 1=temple, 2=water, 3=room
        public float deltaPrev; // difference to previous record in Celsius

        public String getFormattedTime() {
            long diff = System.currentTimeMillis() - timestamp;
            if (diff < 60000) return "Только что";
            if (diff < 3600000) return (diff / 60000) + " мин назад";
            if (diff < 12 * 3600000) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }

        public String getModeLabel() {
            if (mode == 1) return "Висок";
            if (mode == 2) return "Вода";
            if (mode == 3) return "Воздух";
            return "Рука";
        }
    }

    public static class ClinicalReport {
        public int totalCount;
        public float minTemp;
        public float maxTemp;
        public float avgTemp;
        public String statusSummary;
        public String trendStr;
        public String formattedReport;
    }

    public static synchronized void saveRecord(Context context, float body, float skin, int mode) {
        if (context == null || body <= 5.0f) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<Entry> history = getHistory(context);

        float delta = 0f;
        if (!history.isEmpty()) {
            delta = Math.round((body - history.get(0).tempBody) * 10f) / 10f;
        }

        Entry newEntry = new Entry();
        newEntry.timestamp = System.currentTimeMillis();
        newEntry.tempBody = Math.round(body * 10f) / 10f;
        newEntry.tempSkin = Math.round(skin * 10f) / 10f;
        newEntry.mode = mode;
        newEntry.deltaPrev = delta;

        history.add(0, newEntry);
        while (history.size() > MAX_ENTRIES) {
            history.remove(history.size() - 1);
        }

        JSONArray array = new JSONArray();
        for (Entry e : history) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("t", e.timestamp);
                obj.put("b", (double) e.tempBody);
                obj.put("s", (double) e.tempSkin);
                obj.put("m", e.mode);
                obj.put("d", (double) e.deltaPrev);
                array.put(obj);
            } catch (Exception ignored) {}
        }
        sp.edit().putString(PREF_HISTORY_KEY, array.toString()).apply();
    }

    public static synchronized List<Entry> getHistory(Context context) {
        List<Entry> list = new ArrayList<>();
        if (context == null) return list;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = sp.getString(PREF_HISTORY_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Entry e = new Entry();
                e.timestamp = obj.optLong("t");
                e.tempBody = (float) obj.optDouble("b");
                e.tempSkin = (float) obj.optDouble("s");
                e.mode = obj.optInt("m");
                e.deltaPrev = (float) obj.optDouble("d");
                list.add(e);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static synchronized void clearHistory(Context context) {
        if (context == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().remove(PREF_HISTORY_KEY).apply();
    }

    public static ClinicalReport buildClinicalReport(Context context, boolean isFahrenheit) {
        ClinicalReport report = new ClinicalReport();
        List<Entry> history = getHistory(context);
        report.totalCount = history.size();

        if (history.isEmpty()) {
            report.statusSummary = "История измерений пуста";
            report.trendStr = "Нет данных";
            report.formattedReport = "Журнал измерений пуст.\nСделайте замер температуры на руке или виске.";
            return report;
        }

        float minC = Float.MAX_VALUE;
        float maxC = -Float.MAX_VALUE;
        float sumC = 0;
        int bodyCount = 0;

        for (Entry e : history) {
            if (e.mode == 0 || e.mode == 1) { // Wrist or Temple body readings
                minC = Math.min(minC, e.tempBody);
                maxC = Math.max(maxC, e.tempBody);
                sumC += e.tempBody;
                bodyCount++;
            }
        }

        if (bodyCount == 0) {
            report.statusSummary = "Только замеры среды/воды";
            report.trendStr = "—";
            report.formattedReport = "В журнале пока нет замеров температуры тела.";
            return report;
        }

        report.minTemp = minC;
        report.maxTemp = maxC;
        report.avgTemp = Math.round((sumC / bodyCount) * 10f) / 10f;

        // Trend calculation
        Entry latest = history.get(0);
        Entry oldest = history.get(Math.min(history.size() - 1, 4));
        float trendDelta = latest.tempBody - oldest.tempBody;
        if (trendDelta >= 0.3f) {
            report.trendStr = String.format(Locale.US, "Подъём (+%.1f°C)", trendDelta);
        } else if (trendDelta <= -0.3f) {
            report.trendStr = String.format(Locale.US, "Снижение (%.1f°C)", trendDelta);
        } else {
            report.trendStr = "Стабильная динамика";
        }

        if (maxC > 38.5f) {
            report.statusSummary = "Высокая лихорадка";
        } else if (maxC > 38.0f) {
            report.statusSummary = "Умеренная лихорадка";
        } else if (maxC > 37.2f) {
            report.statusSummary = "Субфебрилитет";
        } else if (minC < 35.5f) {
            report.statusSummary = "Гипотермия";
        } else {
            report.statusSummary = "В пределах нормы";
        }

        // Format for on-screen Dialog
        String unit = isFahrenheit ? "°F" : "°C";
        float dispMin = isFahrenheit ? (minC * 1.8f + 32f) : minC;
        float dispMax = isFahrenheit ? (maxC * 1.8f + 32f) : maxC;
        float dispAvg = isFahrenheit ? (report.avgTemp * 1.8f + 32f) : report.avgTemp;

        StringBuilder sb = new StringBuilder();
        sb.append("📊 Статус: ").append(report.statusSummary).append("\n\n");
        sb.append(String.format(Locale.US, "• Максимум: %.1f %s\n", dispMax, unit));
        sb.append(String.format(Locale.US, "• Средняя:  %.1f %s\n", dispAvg, unit));
        sb.append(String.format(Locale.US, "• Минимум:  %.1f %s\n\n", dispMin, unit));
        sb.append("• Тренд: ").append(report.trendStr).append("\n");
        sb.append("• Замеров в базе: ").append(bodyCount);

        report.formattedReport = sb.toString();
        return report;
    }

    public static String exportDoctorReport(Context context) {
        if (context == null) return null;
        List<Entry> history = getHistory(context);
        SimpleDateFormat fullSdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        SimpleDateFormat timeSdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());

        StringBuilder sb = new StringBuilder();
        sb.append("================================================\n");
        sb.append("         МЕДИЦИНСКИЙ ОТЧЕТ О ТЕМПЕРАТУРЕ        \n");
        sb.append("       Xiaomi Watch 5 • Wear OS Thermometer     \n");
        sb.append("================================================\n");
        sb.append("Дата экспорта: ").append(fullSdf.format(new Date())).append("\n");
        sb.append("Всего замеров в журнале: ").append(history.size()).append("\n\n");

        if (history.isEmpty()) {
            sb.append("Журнал пуст. Замеры не проводились.\n");
        } else {
            float minT = Float.MAX_VALUE;
            float maxT = -Float.MAX_VALUE;
            float sumT = 0;
            int count = 0;

            for (Entry e : history) {
                if (e.mode != 2 && e.mode != 3) {
                    minT = Math.min(minT, e.tempBody);
                    maxT = Math.max(maxT, e.tempBody);
                    sumT += e.tempBody;
                    count++;
                }
            }

            if (count > 0) {
                float avgT = sumT / count;
                sb.append("КЛИНИЧЕСКАЯ СВОДКА (ТЕМПЕРАТУРА ТЕЛА):\n");
                sb.append(String.format(Locale.US, "• Максимум: %.1f °C %s\n", maxT, (maxT > 37.2f ? "(Лихорадка/Субфебрилитет!)" : "(Норма)")));
                sb.append(String.format(Locale.US, "• Минимум:  %.1f °C\n", minT));
                sb.append(String.format(Locale.US, "• Средняя:  %.1f °C\n\n", avgT));
            }

            sb.append("ДЕТАЛИЗИРОВАННЫЙ ЖУРНАЛ ЗАМЕРОВ:\n");
            for (int i = 0; i < history.size(); i++) {
                Entry e = history.get(i);
                String deltaStr = "";
                if (e.deltaPrev > 0.05f) deltaStr = String.format(Locale.US, " (+%.1f°↑)", e.deltaPrev);
                else if (e.deltaPrev < -0.05f) deltaStr = String.format(Locale.US, " (%.1f°↓)", e.deltaPrev);
                else deltaStr = " (=)";

                String status = "В норме";
                if (e.mode == 2) {
                    status = "Вода / Ванна";
                } else if (e.mode == 3) {
                    status = "Окружающий воздух";
                } else if (e.tempBody > 38.0f) {
                    status = "Лихорадка / Жар!";
                } else if (e.tempBody > 37.2f) {
                    status = "Субфебрильная";
                } else if (e.tempBody < 35.5f) {
                    status = "Пониженная";
                }

                sb.append(String.format(Locale.US, "[%d] %s | %s: %.1f °C%s | Статус: %s\n",
                        (i + 1), timeSdf.format(new Date(e.timestamp)), e.getModeLabel(), e.tempBody, deltaStr, status));
            }
        }

        sb.append("================================================\n");
        sb.append("Сгенерировано приложением Xiaomi Watch Thermometer\n");

        File targetFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "medical_temp_report.txt");
        try {
            if (!targetFile.getParentFile().exists()) {
                targetFile.getParentFile().mkdirs();
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
                writer.write(sb.toString());
            }
            return targetFile.getAbsolutePath();
        } catch (Exception e) {
            try {
                File fallback = new File(context.getExternalFilesDir(null), "medical_temp_report.txt");
                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(fallback), StandardCharsets.UTF_8)) {
                    writer.write(sb.toString());
                }
                return fallback.getAbsolutePath();
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
