package com.shonux.daytick;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** Glanceable "Today" widget: progress, streak, next 3 tasks. Data is pushed by the app. */
public class TodayWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx));
    }
    static void refreshAll(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, TodayWidget.class));
            for (int id : ids) mgr.updateAppWidget(id, build(ctx));
        } catch (Exception ignored) {}
    }
    static RemoteViews build(Context ctx) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_today);
        String pct = "0%", sub = "Open DayTick to plan your day", l1 = "", l2 = "", l3 = "", streak = "";
        try {
            String json = ctx.getSharedPreferences(DayAlarmPlugin.PREFS, Context.MODE_PRIVATE).getString("widget", "");
            if (!json.isEmpty()) {
                JSONObject o = new JSONObject(json);
                pct = o.optInt("pct") + "%";
                sub = o.optInt("done") + " of " + o.optInt("total") + " done";
                int st = o.optInt("streak"); streak = st > 0 ? "🔥 " + st : "";
                JSONArray a = o.optJSONArray("lines");
                if (a != null) { if (a.length() > 0) l1 = a.optString(0); if (a.length() > 1) l2 = a.optString(1); if (a.length() > 2) l3 = a.optString(2); }
                if (a == null || a.length() == 0) l1 = o.optInt("total") > 0 ? "✨ Everything done" : "☀️ Your day is clear";
            }
        } catch (Exception ignored) {}
        rv.setTextViewText(R.id.w_pct, pct);
        rv.setTextViewText(R.id.w_sub, sub);
        rv.setTextViewText(R.id.w_streak, streak);
        rv.setTextViewText(R.id.w_l1, l1);
        rv.setTextViewText(R.id.w_l2, l2);
        rv.setTextViewText(R.id.w_l3, l3);
        Intent open = new Intent(ctx, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT; if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        rv.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(ctx, 77, open, flags));
        return rv;
    }
}
