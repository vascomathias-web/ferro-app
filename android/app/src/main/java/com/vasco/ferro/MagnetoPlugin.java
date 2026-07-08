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
