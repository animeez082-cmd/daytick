package com.shonux.daytick;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AlarmActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // show over lock screen and turn the screen on
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final String title = getIntent().getStringExtra("title");
        final String taskId = getIntent().getStringExtra("taskId");
        final String day = getIntent().getStringExtra("day");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#14171C"));
        int pad = dp(28);
        root.setPadding(pad, pad, pad, pad);

        TextView clock = new TextView(this);
        clock.setText(android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date()));
        clock.setTextColor(Color.parseColor("#9AA3B0"));
        clock.setTextSize(20);
        clock.setGravity(Gravity.CENTER);

        TextView bell = new TextView(this);
        bell.setText("⏰");
        bell.setTextSize(64);
        bell.setGravity(Gravity.CENTER);
        bell.setPadding(0, dp(20), 0, dp(10));

        TextView tv = new TextView(this);
        tv.setText(title != null ? title : "Reminder");
        tv.setTextColor(Color.parseColor("#ECEDEF"));
        tv.setTextSize(30);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 0, 0, dp(40));

        Button done = bigButton("✓  Done", "#5FD3A6", "#0B2A1E");
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishWith("done", taskId, day); }
        });

        Button snooze = bigButton("Snooze 30 min", "#242B34", "#ECEDEF");
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finishWith("snooze", taskId, day); }
        });

        root.addView(clock);
        root.addView(bell);
        root.addView(tv);
        root.addView(done);
        root.addView(snooze);
        setContentView(root);
    }

    private Button bigButton(String text, String bg, String fg) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(18);
        btn.setTextColor(Color.parseColor(fg));
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor(bg));
        gd.setCornerRadius(dp(16));
        btn.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        lp.topMargin = dp(12);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void finishWith(String action, String taskId, String day) {
        // stop the ringing
        Intent stop = new Intent(this, AlarmService.class);
        stop.setAction("STOP");
        startService(stop);
        // tell the web app what happened, so it updates + reschedules on next open
        try {
            getSharedPreferences("daytick_alarm", MODE_PRIVATE)
                    .edit()
                    .putString("pending", action + "|" + (taskId == null ? "" : taskId) + "|" + (day == null ? "" : day))
                    .apply();
        } catch (Exception ignored) {}
        finish();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override public void onBackPressed() { /* block back so it isn't dismissed accidentally */ }
}
