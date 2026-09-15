package com.shonux.daytick;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent svc = new Intent(ctx, AlarmService.class);
        svc.setAction("RING");
        svc.putExtra("title", intent.getStringExtra("title"));
        svc.putExtra("taskId", intent.getStringExtra("taskId"));
        svc.putExtra("day", intent.getStringExtra("day"));
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
        else ctx.startService(svc);
    }
}
