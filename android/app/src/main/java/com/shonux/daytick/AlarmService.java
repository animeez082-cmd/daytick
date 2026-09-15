package com.shonux.daytick;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AlarmService extends Service {

    static final String CH = "daytick_alarm";
    static final int NOTIF_ID = 424242;
    private Ringtone ringtone;
    private Vibrator vibrator;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "RING";
        if ("STOP".equals(action)) { stopEverything(); return START_NOT_STICKY; }

        String title = intent != null ? intent.getStringExtra("title") : "Reminder";
        String taskId = intent != null ? intent.getStringExtra("taskId") : "";
        String day = intent != null ? intent.getStringExtra("day") : "";
        if (title == null) title = "Reminder";

        ensureChannel();

        // full-screen intent → opens ringing screen even on lock screen
        Intent full = new Intent(this, AlarmActivity.class);
        full.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        full.putExtra("title", title);
        full.putExtra("taskId", taskId);
        full.putExtra("day", day);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fsPi = PendingIntent.getActivity(this, 1001, full, piFlags);

        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        b.setContentTitle("⏰ " + title)
         .setContentText("Tap to open · reminder")
         .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
         .setCategory(Notification.CATEGORY_ALARM)
         .setPriority(Notification.PRIORITY_MAX)
         .setOngoing(true)
         .setAutoCancel(false)
         .setFullScreenIntent(fsPi, true)
         .setContentIntent(fsPi);

        Notification n = b.build();
        startForeground(NOTIF_ID, n);

        startSound();
        return START_STICKY;
    }

    private void startSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= 21) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                }
                if (Build.VERSION.SDK_INT >= 28) ringtone.setLooping(true);
                ringtone.play();
            }
        } catch (Exception ignored) {}
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = {0, 600, 500};
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= 26)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
                else vibrator.vibrate(pattern, 0);
            }
        } catch (Exception ignored) {}
    }

    private void stopEverything() {
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {}
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
        if (Build.VERSION.SDK_INT >= 24) stopForeground(Service.STOP_FOREGROUND_REMOVE);
        else stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() {
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {}
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch = nm.getNotificationChannel(CH);
            if (ch == null) {
                ch = new NotificationChannel(CH, "Alarms", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Full-screen task alarms");
                ch.setSound(null, null); // service plays the sound itself, on the alarm stream
                ch.enableVibration(false);
                ch.setBypassDnd(true);
                ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(ch);
            }
        }
    }
}
