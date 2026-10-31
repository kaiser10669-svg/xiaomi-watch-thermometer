package com.kaiserdev.watchthermometer;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.support.wearable.complications.ComplicationData;
import android.support.wearable.complications.ComplicationText;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

public class ThermometerComplicationService extends Service {

    public static final String PREFS_NAME = "watch_thermometer_prefs";
    public static final String KEY_SKIN_TEMP = "last_skin_temp";
    public static final String KEY_BODY_EST = "last_body_est";
    public static final String KEY_AMBIENT_TEMP = "last_ambient_temp";
    public static final String KEY_TIMESTAMP = "last_update_time";

    private final Binder mBinder = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case 1: { // onUpdate
                    data.enforceInterface("android.support.wearable.complications.IComplicationProvider");
                    int complicationId = data.readInt();
                    int type = data.readInt();
                    IBinder manager = data.readStrongBinder();
                    handleUpdate(complicationId, type, manager);
                    return true;
                }
                case 2: { // onComplicationActivated
                    data.enforceInterface("android.support.wearable.complications.IComplicationProvider");
                    int complicationId = data.readInt();
                    int type = data.readInt();
                    IBinder manager = data.readStrongBinder();
                    handleUpdate(complicationId, type, manager);
                    return true;
                }
                case 3: { // onComplicationDeactivated
                    data.enforceInterface("android.support.wearable.complications.IComplicationProvider");
                    int complicationId = data.readInt();
                    return true;
                }
                case 1598968902: { // INTERFACE_TRANSACTION
                    if (reply != null) {
                        reply.writeString("android.support.wearable.complications.IComplicationProvider");
                    }
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private static int sCachedSkinZone = -1;

    private static float readDirectSysfsSkinTemp() {
        try {
            if (sCachedSkinZone < 0) {
                for (int i = 0; i <= 24; i++) {
                    File typeFile = new File("/sys/class/thermal/thermal_zone" + i + "/type");
                    if (typeFile.exists() && typeFile.canRead()) {
                        BufferedReader br = new BufferedReader(new FileReader(typeFile));
                        String line = br.readLine();
                        br.close();
                        if (line != null) {
                            String type = line.trim().toLowerCase(Locale.US);
                            if (type.contains("sdm-skin-therm") || type.contains("skin")) {
                                sCachedSkinZone = i;
                                break;
                            }
                        }
                    }
                }
            }
            if (sCachedSkinZone >= 0) {
                File f = new File("/sys/class/thermal/thermal_zone" + sCachedSkinZone + "/temp");
                if (f.exists() && f.canRead()) {
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    String line = br.readLine();
                    br.close();
                    if (line != null) {
                        float raw = Float.parseFloat(line.trim());
                        if (raw > 0) {
                            return raw > 1000 ? raw / 1000.0f : (raw > 100 ? raw / 10.0f : raw);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            sCachedSkinZone = -1;
        }
        return -1.0f;
    }

    private void handleUpdate(int complicationId, int type, IBinder manager) {
        if (manager == null) return;

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        float skinTemp = sp.getFloat(KEY_SKIN_TEMP, -1.0f);
        float bodyEst = sp.getFloat(KEY_BODY_EST, -1.0f);
        float tempOffset = sp.getFloat("temp_offset", 0.0f);
        boolean isFahrenheit = sp.getBoolean("is_fahrenheit", false);
        long lastTime = sp.getLong(KEY_TIMESTAMP, 0);

        // If no cache or older than 10 minutes, sample direct sysfs hardware zone or battery gauge on the fly
        if (skinTemp <= 0 || (System.currentTimeMillis() - lastTime > 600000)) {
            float directZone = readDirectSysfsSkinTemp();
            if (directZone >= 20.0f && directZone <= 45.0f) {
                skinTemp = directZone;
                float base = CircadianModel.getBaseline(System.currentTimeMillis());
                bodyEst = base + (skinTemp - 33.0f) * 0.45f + tempOffset;
            } else {
                try {
                    Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (bi != null) {
                        int status = bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                        int plugged = bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                        boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                        boolean isPlugged = (plugged != 0);

                        // If watch is on charging dock, ignore hot battery readings to avoid false complication fever
                        if (!isCharging && !isPlugged) {
                            int btemp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                            if (btemp > 0) {
                                float batteryC = btemp / 10.0f;
                                if (batteryC >= 26.5f) {
                                    skinTemp = 32.1f + (batteryC - 28.0f) * 0.42f;
                                    if (skinTemp < 30.5f) skinTemp = 30.5f;
                                    if (skinTemp > 35.8f) skinTemp = 35.8f;
                                    float base = CircadianModel.getBaseline(System.currentTimeMillis());
                                    bodyEst = base + (skinTemp - 33.0f) * 0.42f + tempOffset;
                                } else {
                                    skinTemp = batteryC;
                                    bodyEst = CircadianModel.getBaseline(System.currentTimeMillis()) + tempOffset;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (skinTemp <= 0) {
            skinTemp = 33.4f;
            bodyEst = 36.6f + tempOffset;
        }

        if (bodyEst < 34.0f) bodyEst = 34.0f;
        if (bodyEst > 42.0f) bodyEst = 42.0f;

        float dispSkin = isFahrenheit ? (skinTemp * 1.8f + 32f) : skinTemp;
        float dispBody = isFahrenheit ? (bodyEst * 1.8f + 32f) : bodyEst;
        String tempStr = String.format(Locale.US, "%.1f°", dispSkin);
        String bodyStr = String.format(Locale.US, "%.1f°", dispBody);

        Intent launchIntent = new Intent(this, MainActivity.class);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent tapAction = PendingIntent.getActivity(this, complicationId, launchIntent, piFlags);
        Icon icon = Icon.createWithResource(this, R.drawable.ic_complication_temp);

        boolean isRu = Locale.getDefault().getLanguage().equalsIgnoreCase("ru");
        String shortTitle = isRu ? "ТЕЛО" : "BODY";
        String longTitle = isRu ? "Термометр" : "Thermometer";
        String longText = isRu ? ("Тело: " + bodyStr + " • Кожа: " + tempStr) : ("Body: " + bodyStr + " • Skin: " + tempStr);

        ComplicationData.Builder builder;
        if (type == ComplicationData.TYPE_SHORT_TEXT) {
            builder = new ComplicationData.Builder(ComplicationData.TYPE_SHORT_TEXT)
                    .setShortText(ComplicationText.plainText(bodyStr))
                    .setShortTitle(ComplicationText.plainText(shortTitle))
                    .setIcon(icon)
                    .setTapAction(tapAction);
        } else if (type == ComplicationData.TYPE_RANGED_VALUE) {
            float minVal = isFahrenheit ? (34.0f * 1.8f + 32f) : 34.0f;
            float maxVal = isFahrenheit ? (42.0f * 1.8f + 32f) : 42.0f;
            float curVal = isFahrenheit ? dispBody : bodyEst;
            builder = new ComplicationData.Builder(ComplicationData.TYPE_RANGED_VALUE)
                    .setRangedValue(curVal)
                    .setRangedMinValue(minVal)
                    .setRangedMaxValue(maxVal)
                    .setShortText(ComplicationText.plainText(bodyStr))
                    .setShortTitle(ComplicationText.plainText(shortTitle))
                    .setIcon(icon)
                    .setTapAction(tapAction);
        } else if (type == ComplicationData.TYPE_LONG_TEXT) {
            builder = new ComplicationData.Builder(ComplicationData.TYPE_LONG_TEXT)
                    .setLongText(ComplicationText.plainText(longText))
                    .setLongTitle(ComplicationText.plainText(longTitle))
                    .setIcon(icon)
                    .setTapAction(tapAction);
        } else if (type == ComplicationData.TYPE_ICON) {
            builder = new ComplicationData.Builder(ComplicationData.TYPE_ICON)
                    .setIcon(icon)
                    .setTapAction(tapAction);
        } else {
            builder = new ComplicationData.Builder(ComplicationData.TYPE_NO_DATA);
        }

        sendComplicationData(manager, complicationId, builder.build());
    }

    private static void sendComplicationData(IBinder manager, int complicationId, ComplicationData data) {
        if (manager == null) return;
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeInterfaceToken("android.support.wearable.complications.IComplicationManager");
            parcel.writeInt(complicationId);
            if (data != null) {
                parcel.writeInt(1);
                data.writeToParcel(parcel, 0);
            } else {
                parcel.writeInt(0);
            }
            manager.transact(1, parcel, null, IBinder.FLAG_ONEWAY);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            parcel.recycle();
        }
    }

    public static void requestComplicationUpdate(Context context) {
        if (context == null) return;
        try {
            ComponentName comp = new ComponentName(context, ThermometerComplicationService.class);

            // 1. Wear OS system broadcast with explicit package targeting
            Intent intent1 = new Intent("android.support.wearable.complications.ACTION_REQUEST_UPDATE_ALL");
            intent1.setPackage("com.google.android.wearable.app");
            intent1.putExtra("android.support.wearable.complications.EXTRA_PROVIDER_COMPONENT", comp);
            context.sendBroadcast(intent1);

            // 2. Broadcast without package restriction for OEM watchfaces (Xiaomi MiWear, WatchMaker, Pujie)
            Intent intent2 = new Intent("android.support.wearable.complications.ACTION_REQUEST_UPDATE_ALL");
            intent2.putExtra("android.support.wearable.complications.EXTRA_PROVIDER_COMPONENT", comp);
            context.sendBroadcast(intent2);

            // 3. AndroidX / Jetpack Wear OS 3+ / 4+ modern watchface complication protocol
            Intent intent3 = new Intent("androidx.wear.watchface.complications.action.REQUEST_UPDATE_ALL");
            intent3.setPackage("com.google.android.wearable.app");
            intent3.putExtra("androidx.wear.watchface.complications.extra.PROVIDER_COMPONENT", comp);
            context.sendBroadcast(intent3);
        } catch (Exception ignored) {}
    }
}
