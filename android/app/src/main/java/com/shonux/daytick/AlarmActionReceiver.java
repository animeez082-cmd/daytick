package com.shonux.daytick;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles Done / Snooze / Stop taps on the alarm NOTIFICATION itself (works even if the
 *  full-screen alarm UI never opened). Records the action for the web app, stops sound. */
public class AlarmActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getStringExtra("action");
        String taskId = intent.getStringExtra("taskId");
        String day = intent.getStringExtra("day");
        String kind = intent.getStringExtra("kind");
        int notifId = intent.getIntExtra("notifId", 0);
        if (action == null) action = "stop";
        try {
            ctx.getSharedPreferences(DayAlarmPlugin.PREFS, Context.MODE_PRIVATE).edit()
               .putString("pending", action + "|" + (taskId == null ? "" : taskId) + "|" + (day == null ? "" : day) + "|" + (kind == null ? "alarm" : kind)).apply();
        } catch (Exception ignored) {}
        try { Intent stop = new Intent(ctx, AlarmService.class); stop.setAction("STOP"); ctx.startService(stop); } catch (Exception ignored) {}
        try { NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE); if (nm != null) { if (notifId != 0) nm.cancel(notifId); nm.cancel(AlarmService.NOTIF_ID); } } catch (Exception ignored) {}
        // if the alarm screen is open, close it
        try { Intent close = new Intent("com.shonux.daytick.CLOSE_ALARM"); close.setPackage(ctx.getPackageName()); ctx.sendBroadcast(close); } catch (Exception ignored) {}
        // "done" / "snooze" need the app to update + reschedule → wake it briefly in background
        if ("done".equals(action) || "snooze".equals(action) || "start".equals(action) || "add5".equals(action) || "add10".equals(action)) {
            try { Intent open = new Intent(ctx, MainActivity.class); open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); open.putExtra("fromAlarm", true); ctx.startActivity(open); } catch (Exception ignored) {}
        }
    }
}
