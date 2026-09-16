package com.shonux.daytick;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Premium full-screen alarm. Themed from the app's saved theme, big readable controls. */
public class AlarmActivity extends Activity {

    private Ringtone ringtone;
    private Vibrator vibrator;
    private ValueAnimator pulse;

    // theme → [bgTop, bgBottom, accent, accentInk]
    private static String[] palette(String theme) {
        if (theme == null) theme = "midnight";
        switch (theme) {
            case "ocean":    return new String[]{"#0B1B26", "#04101A", "#4FC3F7", "#04202C"};
            case "forest":   return new String[]{"#0F1F16", "#07120C", "#7BD88F", "#06210F"};
            case "lavender": return new String[]{"#1C1630", "#0E0A1C", "#B48CF2", "#1B0F2E"};
            case "sunset":   return new String[]{"#2A1612", "#140907", "#F0935A", "#2A1206"};
            case "cosmic":   return new String[]{"#0F1130", "#05061A", "#8EA2FF", "#0A0F33"};
            case "rain":     return new String[]{"#101820", "#080D13", "#7FB3D5", "#06202E"};
            case "snow":     return new String[]{"#16202B", "#0B1118", "#CFE6F7", "#0A1B26"};
            case "cafe":     return new String[]{"#241A12", "#120C08", "#E0B073", "#2A1806"};
            case "neon":     return new String[]{"#0D0A1E", "#05030F", "#FF6BD5", "#2A0620"};
            case "light":    return new String[]{"#F3F5F9", "#E3E8F0", "#E0A020", "#1A1400"};
            default:         return new String[]{"#1A1D24", "#0C0E12", "#F5B942", "#1A1400"};
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        playAlarm();

        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final String title  = getIntent().getStringExtra("title");
        final String taskId = getIntent().getStringExtra("taskId");
        final String day    = getIntent().getStringExtra("day");
        final String kind   = getIntent().getStringExtra("kind") == null ? "alarm" : getIntent().getStringExtra("kind");
        String theme = getSharedPreferences("daytick_alarm", MODE_PRIVATE).getString("theme", "midnight");
        String emoji = getSharedPreferences("daytick_alarm", MODE_PRIVATE).getString("emoji_" + taskId, "⏰");
        String[] p = palette(theme);
        boolean light = "light".equals(theme);
        int ink  = light ? Color.parseColor("#151A21") : Color.parseColor("#F2F4F7");
        int ink2 = light ? Color.parseColor("#5B6572") : Color.parseColor("#A2ACBA");
        int accent = Color.parseColor(p[2]);

        // ---- background gradient ----
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.parseColor(p[0]), Color.parseColor(p[1])});
        root.setBackground(bg);

        // soft accent glow blob behind the icon
        View glow = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColors(new int[]{withAlpha(accent, 0x55), withAlpha(accent, 0x00)});
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(dp(220));
        glow.setBackground(g);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(dp(440), dp(440), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        gl.topMargin = dp(40);
        root.addView(glow, gl);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(28);
        col.setPadding(pad, dp(56), pad, dp(28));

        TextView label = text("timed".equals(kind) ? "TIME'S UP" : "start".equals(kind) ? "TIME TO START" : "DAYTICK ALARM", 13, ink2, false);
        label.setLetterSpacing(0.18f);
        label.setGravity(Gravity.CENTER);

        TextView clock = text(android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date()), 64, ink, true);
        clock.setGravity(Gravity.CENTER);
        clock.setLetterSpacing(-0.03f);
        clock.setPadding(0, dp(6), 0, dp(26));

        // icon circle
        FrameLayout iconWrap = new FrameLayout(this);
        View ring = new View(this);
        GradientDrawable rd = new GradientDrawable();
        rd.setShape(GradientDrawable.OVAL);
        rd.setColor(withAlpha(accent, 0x22));
        rd.setStroke(dp(2), withAlpha(accent, 0x88));
        ring.setBackground(rd);
        iconWrap.addView(ring, new FrameLayout.LayoutParams(dp(132), dp(132), Gravity.CENTER));
        TextView ico = text(emoji, 56, ink, false);
        ico.setGravity(Gravity.CENTER);
        iconWrap.addView(ico, new FrameLayout.LayoutParams(dp(132), dp(132), Gravity.CENTER));
        LinearLayout.LayoutParams iw = new LinearLayout.LayoutParams(dp(132), dp(132));
        iw.bottomMargin = dp(22);
        iconWrap.setLayoutParams(iw);

        // gentle pulse on the ring
        pulse = ValueAnimator.ofFloat(1f, 1.08f);
        pulse.setDuration(900); pulse.setRepeatMode(ValueAnimator.REVERSE); pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        final View ringRef = ring;
        pulse.addUpdateListener(a -> { float v = (float) a.getAnimatedValue(); ringRef.setScaleX(v); ringRef.setScaleY(v); });
        pulse.start();

        TextView tv = text(title != null ? title.replace("Time's up: ", "") : "Reminder", 28, ink, true);
        tv.setGravity(Gravity.CENTER);
        tv.setLineSpacing(0, 1.1f);
        tv.setPadding(0, 0, 0, dp(6));

        TextView sub = text("timed".equals(kind) ? "Your timed task has finished." : "start".equals(kind) ? "Tap Started when you begin." : "Tap Done when it's handled.", 15, ink2, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, dp(28));

        col.addView(label); col.addView(clock); col.addView(iconWrap); col.addView(tv); col.addView(sub);

        // spacer
        View sp = new View(this); col.addView(sp, new LinearLayout.LayoutParams(1, 0, 1f));

        // ---- buttons ----
        String[] pri = { "✓  Done", p[2], p[3] };
        Button b1, b2, b3 = null;
        String secBg = light ? "#FFFFFF" : "#FFFFFF1A"; String secFg = light ? "#151A21" : "#F2F4F7";
        if ("timed".equals(kind)) {
            b1 = bigButton("✓  Done", pri[1], pri[2], true);
            b1.setOnClickListener(v -> finishWith("done", taskId, day, kind));
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            b2 = bigButton("+ 5 min", secBg, secFg, false);
            b3 = bigButton("+ 10 min", secBg, secFg, false);
            b2.setOnClickListener(v -> finishWith("add5", taskId, day, kind));
            b3.setOnClickListener(v -> finishWith("add10", taskId, day, kind));
            LinearLayout.LayoutParams h1 = new LinearLayout.LayoutParams(0, dp(58), 1f); h1.rightMargin = dp(6);
            LinearLayout.LayoutParams h2 = new LinearLayout.LayoutParams(0, dp(58), 1f); h2.leftMargin = dp(6);
            row.addView(b2, h1); row.addView(b3, h2);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); rl.topMargin = dp(10);
            col.addView(b1); col.addView(row, rl);
        } else if ("start".equals(kind)) {
            b1 = bigButton("Started  ▶", pri[1], pri[2], true);
            b1.setOnClickListener(v -> finishWith("start", taskId, day, kind));
            b2 = bigButton("Snooze 10 min", secBg, secFg, false);
            b2.setOnClickListener(v -> finishWith("snooze", taskId, day, kind));
            col.addView(b1); col.addView(b2);
        } else {
            b1 = bigButton("✓  Done", pri[1], pri[2], true);
            b1.setOnClickListener(v -> finishWith("done", taskId, day, kind));
            b2 = bigButton("Snooze 10 min", secBg, secFg, false);
            b2.setOnClickListener(v -> finishWith("snooze", taskId, day, kind));
            col.addView(b1); col.addView(b2);
        }
        TextView stop = text("Stop sound", 14, ink2, false);
        stop.setGravity(Gravity.CENTER); stop.setPadding(0, dp(18), 0, 0);
        stop.setOnClickListener(v -> { stopAlarm(); try { Intent s = new Intent(this, AlarmService.class); s.setAction("STOP"); startService(s);} catch (Exception ignored) {} });
        col.addView(stop);

        root.addView(col, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // enter animation
        col.setAlpha(0f); col.setTranslationY(dp(24));
        col.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button bigButton(String text, String bg, String fg, boolean primary) {
        Button btn = new Button(this);
        btn.setText(text); btn.setAllCaps(false); btn.setTextSize(17);
        btn.setTextColor(Color.parseColor(fg));
        if (primary) btn.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bg));
        gd.setCornerRadius(dp(18));
        if (!primary) gd.setStroke(dp(1), Color.parseColor("#FFFFFF22"));
        btn.setBackground(gd);
        btn.setElevation(primary ? dp(6) : 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60));
        lp.topMargin = dp(10);
        btn.setLayoutParams(lp);
        btn.setOnTouchListener((v, e) -> {
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN) v.animate().scaleX(.96f).scaleY(.96f).setDuration(80).start();
            else if (e.getAction() == android.view.MotionEvent.ACTION_UP || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            return false;
        });
        return btn;
    }

    private static int withAlpha(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }

    private void finishWith(String action, String taskId, String day, String kind) {
        stopAlarm();
        try { Intent stop = new Intent(this, AlarmService.class); stop.setAction("STOP"); startService(stop); } catch (Exception ignored) {}
        try { android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); if (nm != null) nm.cancelAll(); } catch (Exception ignored) {}
        try {
            getSharedPreferences("daytick_alarm", MODE_PRIVATE).edit()
                    .putString("pending", action + "|" + (taskId == null ? "" : taskId) + "|" + (day == null ? "" : day) + "|" + kind).apply();
        } catch (Exception ignored) {}
        finish();
    }

    private void playAlarm() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= 21) ringtone.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                if (Build.VERSION.SDK_INT >= 28) ringtone.setLooping(true);
                ringtone.play();
            }
        } catch (Exception ignored) {}
        try {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            long[] pattern = {0, 600, 400, 600};
            if (vibrator != null) { if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)); else vibrator.vibrate(pattern, 0); }
        } catch (Exception ignored) {}
    }
    private void stopAlarm() {
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {}
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
        try { if (pulse != null) pulse.cancel(); } catch (Exception ignored) {}
    }
    @Override protected void onDestroy() { stopAlarm(); super.onDestroy(); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    @Override public void onBackPressed() { }
}
