package com.kaiserdev.watchthermometer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TempLogManager {

    private static final String PREFS_NAME = "watch_thermometer_prefs";
    private static final String PREF_HISTORY_KEY = "temp_history_json";
    private static final int MAX_ENTRIES = 12;

    public static class Entry {
        public long timestamp;
        public float tempBody;
        public float tempSkin;
        public int mode; // 0=wrist, 1=temple, 2=room
        public float deltaPrev; // difference to previous record

        public String getFormattedTime() {
            long diff = System.currentTimeMillis() - timestamp;
            if (diff < 60000) return "Только что";
            if (diff < 3600000) return (diff / 60000) + " мин назад";
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }

        public String getModeLabel() {
            if (mode == 1) return "Висок";
            if (mode == 2) return "Воздух";
            return "Рука";
        }
    }

    public static synchronized void saveRecord(Context context, float body, float skin, int mode) {
        if (context == null || body <= 15.0f) return;
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
}
