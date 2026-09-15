package com.shonux.daytick;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DayAlarmPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
