package com.vasco.ferro;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MagnetoPlugin.class);   // doit être enregistré AVANT super.onCreate
        super.onCreate(savedInstanceState);
    }
}
