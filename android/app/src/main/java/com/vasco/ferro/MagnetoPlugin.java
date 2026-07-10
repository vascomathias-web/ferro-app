package com.vasco.ferro;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Plugin natif : lit le vrai magnétomètre (TYPE_MAGNETIC_FIELD) via le SensorManager Android.
 * Les valeurs sont en microteslas (µT), sans la restriction de l'API web Magnetometer.
 */
@CapacitorPlugin(name = "Magneto")
public class MagnetoPlugin extends Plugin implements SensorEventListener {

    private SensorManager sm;
    private Sensor mag;

    @Override
    public void load() {
        sm = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        if (sm != null) {
            mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
    }

    @PluginMethod
    public void isAvailable(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("available", mag != null);
        call.resolve(ret);
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (sm == null || mag == null) {
            call.reject("Aucun magnétomètre sur cet appareil");
            return;
        }
        int freq = call.getInt("frequency", 50);
        if (freq < 1) freq = 1;
        int periodUs = 1000000 / freq;                 // période d'échantillonnage en microsecondes
        sm.registerListener(this, mag, periodUs);
        JSObject ret = new JSObject();
        ret.put("available", true);
        call.resolve(ret);
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (sm != null) {
            sm.unregisterListener(this);
        }
        call.resolve();
    }

    @PluginMethod
    public void vibrate(PluginCall call) {
        int ms = call.getInt("ms", 30);
        Vibrator v;
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = (VibratorManager) getContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            v = (vm != null) ? vm.getDefaultVibrator() : null;
        } else {
            v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void saveImage(PluginCall call) {
        String data = call.getString("data");
        if (data == null) { call.reject("Aucune donnée image"); return; }
        if (data.contains(",")) data = data.substring(data.indexOf(',') + 1);
        try {
            byte[] bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
            String name = "Ferro_" + System.currentTimeMillis() + ".jpg";
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
            cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Ferro");
            }
            android.content.ContentResolver r = getContext().getContentResolver();
            android.net.Uri uri = r.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) { call.reject("Impossible de créer le fichier"); return; }
            java.io.OutputStream os = r.openOutputStream(uri);
            os.write(bytes);
            os.close();
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Erreur d'enregistrement");
        }
    }

    @PluginMethod
    public void shareImage(PluginCall call) {
        String data = call.getString("data");
        if (data == null) { call.reject("Aucune donnée image"); return; }
        if (data.contains(",")) data = data.substring(data.indexOf(',') + 1);
        try {
            byte[] bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT);
            // écrit l'image dans le cache (couvert par le FileProvider : cache-path ".")
            java.io.File dir = new java.io.File(getContext().getCacheDir(), "shared");
            dir.mkdirs();
            java.io.File f = new java.io.File(dir, "Ferro_" + System.currentTimeMillis() + ".jpg");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(f);
            fos.write(bytes);
            fos.close();
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                getContext(), getContext().getPackageName() + ".fileprovider", f);
            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND);
            send.setType("image/jpeg");
            send.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            send.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            android.content.Intent chooser = android.content.Intent.createChooser(send, "Partager le scan");
            chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Erreur de partage");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        JSObject data = new JSObject();
        data.put("x", event.values[0]);
        data.put("y", event.values[1]);
        data.put("z", event.values[2]);
        notifyListeners("reading", data);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // non utilisé
    }
}
