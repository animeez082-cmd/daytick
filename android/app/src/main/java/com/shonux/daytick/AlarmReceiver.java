package com.shonux.daytick;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

/**
 * Fires when an exact alarm goes off — even if the app is closed or the phone is idle.
 * On modern Android you CANNOT reliably start a foreground service from here, but you
 * CAN post a high-importance notification with a full-screen intent. That is the
 * platform-sanctioned way to raise an alarm screen from the background, so we do that
 * first (guaranteed to wake the user), then also try to start the ringing service for
 * the looping sound where the OS still allows it.
 */
public class AlarmReceiver extends BroadcastReceiver {
    static final String CH = "daytick_alarm";
    static final int BASE = 500000;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String title = intent.getStringExtra("title");
        String taskId = intent.getStringExtra("taskId");
        String day = intent.getStringExtra("day");
        String kind = intent.getStringExtra("kind");
        int reqId = intent.getIntExtra("reqId", 1);
        if (title == null) title = "Reminder";
        if (kind == null) kind = "alarm";

        // wake the screen
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                    "daytick:alarm");
            wl.acquire(8000);
        } catch (Exception ignored) {}

        ensureChannel(ctx);

        Intent full = new Intent(ctx, AlarmActivity.class);
        full.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        full.putExtra("title", title);
        full.putExtra("taskId", taskId);
        full.putExtra("day", day);
        full.putExtra("kind", kind);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fsPi = PendingIntent.getActivity(ctx, reqId, full, piFlags);

        String emoji = ctx.getSharedPreferences(DayAlarmPlugin.PREFS, Context.MODE_PRIVATE).getString("emoji_" + taskId, "⏰");
        String head = "timed".equals(kind) ? "Time's up" : "start".equals(kind) ? "Time to start" : "DayTick alarm";
        String when = android.text.format.DateFormat.getTimeFormat(ctx).format(new java.util.Date());
        int notifId = BASE + reqId;
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(ctx, CH) : new Notification.Builder(ctx);
        b.setContentTitle(emoji + "  " + title.replace("Time's up: ", ""))
         .setContentText(head + " · " + when)
         .setSubText("DayTick")
         .setStyle(new Notification.BigTextStyle().bigText(head + " · " + when + "\nTap Done when it's handled, or Snooze for 10 minutes."))
         .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
         .setColor(0xFFF5B942)
         .setCategory(Notification.CATEGORY_ALARM)
         .setPriority(Notification.PRIORITY_MAX)
         .setAutoCancel(false)
         .setOngoing(true)
         .setFullScreenIntent(fsPi, true)
         .setContentIntent(fsPi);
        if (Build.VERSION.SDK_INT >= 26) b.setColorized(true);
        // action buttons on the notification itself
        if ("timed".equals(kind)) {
            b.addAction(actionPi(ctx, "✓ Done", "done", taskId, day, kind, notifId, reqId * 10 + 1));
            b.addAction(actionPi(ctx, "+5 min", "add5", taskId, day, kind, notifId, reqId * 10 + 2));
            b.addAction(actionPi(ctx, "+10 min", "add10", taskId, day, kind, notifId, reqId * 10 + 3));
        } else if ("start".equals(kind)) {
            b.addAction(actionPi(ctx, "▶ Started", "start", taskId, day, kind, notifId, reqId * 10 + 1));
            b.addAction(actionPi(ctx, "Snooze 10m", "snooze", taskId, day, kind, notifId, reqId * 10 + 2));
        } else {
            b.addAction(actionPi(ctx, "✓ Done", "done", taskId, day, kind, notifId, reqId * 10 + 1));
            b.addAction(actionPi(ctx, "Snooze 10m", "snooze", taskId, day, kind, notifId, reqId * 10 + 2));
            b.addAction(actionPi(ctx, "Stop", "stop", taskId, day, kind, notifId, reqId * 10 + 3));
        }
        if (Build.VERSION.SDK_INT >= 21) b.setVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notifId, b.build());

        // Directly launch the ringing screen too. On many devices the activity start from
        // a background alarm receiver is permitted (SYSTEM_ALERT_WINDOW / recent alarm), and
        // the full-screen intent above is the guaranteed fallback if the OS blocks this.
        boolean locked = false;
        try { KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE); locked = km != null && km.isKeyguardLocked(); } catch (Exception ignored) {}
        try { ctx.startActivity(full); } catch (Exception ignored) {}

        // Try to start the looping-sound service (allowed on Android < 14 from here; guarded on 14+).
        try {
            Intent svc = new Intent(ctx, AlarmService.class);
            svc.setAction("RING");
            svc.putExtra("title", title);
            svc.putExtra("taskId", taskId);
            svc.putExtra("day", day);
            svc.putExtra("kind", kind);
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
            else ctx.startService(svc);
        } catch (Exception ignored) {
            // Blocked on this OS version — the full-screen-intent notification (with its own
            // channel sound) still alerts the user, and AlarmActivity plays the ringtone.
        }
    }

    static Notification.Action actionPi(Context ctx, String label, String action, String taskId, String day, String kind, int notifId, int req) {
        Intent i = new Intent(ctx, AlarmActionReceiver.class);
        i.setAction("com.shonux.daytick.ACT." + action + "." + req);
        i.putExtra("action", action); i.putExtra("taskId", taskId); i.putExtra("day", day); i.putExtra("kind", kind); i.putExtra("notifId", notifId);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT; if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(ctx, req, i, flags);
        if (Build.VERSION.SDK_INT >= 23) return new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(ctx, android.R.drawable.ic_menu_agenda), label, pi).build();
        return new Notification.Action.Builder(android.R.drawable.ic_menu_agenda, label, pi).build();
    }

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CH) == null) {
                NotificationChannel ch = new NotificationChannel(CH, "Alarms", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Full-screen task alarms");
                ch.setBypassDnd(true);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                // Silent channel: AlarmActivity plays the ringtone + vibration itself, so the
                // notification must not add a second overlapping sound.
                ch.setSound(null, null);
                ch.enableVibration(false);
                nm.createNotificationChannel(ch);
            }
        }
    }
}
