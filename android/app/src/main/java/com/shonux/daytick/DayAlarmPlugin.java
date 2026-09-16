package com.shonux.daytick;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(name = "DayAlarm")
public class DayAlarmPlugin extends Plugin {

    static final String PREFS = "daytick_alarm";

    static PendingIntent piFor(Context ctx, int id, String title, String taskId, String day, String kind) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.setAction("com.shonux.daytick.RING." + id);
        i.putExtra("title", title);
        i.putExtra("taskId", taskId);
        i.putExtra("day", day);
        i.putExtra("kind", kind);
        i.putExtra("reqId", id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, id, i, flags);
    }

    static void scheduleOne(Context ctx, int id, long at, String title, String taskId, String day, String kind) {
        AlarmManager mgr = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = piFor(ctx, id, title, taskId, day, kind);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !mgr.canScheduleExactAlarms()) {
                mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                mgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                mgr.setExact(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException se) {
            mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    @PluginMethod
    public void setAlarms(PluginCall call) {
        JSONArray arr = call.getData().optJSONArray("alarms");
        if (arr == null) arr = new JSONArray();
        Context ctx = getContext();
        for (int k = 0; k < arr.length(); k++) {
            JSONObject o = arr.optJSONObject(k);
            if (o == null) continue;
            scheduleOne(ctx, o.optInt("id"), o.optLong("at"), o.optString("title", "Reminder"),
                    o.optString("taskId", ""), o.optString("day", ""), o.optString("kind", "alarm"));
        }
        // persist so BootReceiver can re-register after a restart; also theme + per-task emoji for the alarm screen
        SharedPreferences.Editor ed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("alarms", arr.toString());
        String theme = call.getString("theme"); if (theme != null) ed.putString("theme", theme);
        for (int k = 0; k < arr.length(); k++) { JSONObject o = arr.optJSONObject(k); if (o != null && o.has("emoji")) ed.putString("emoji_" + o.optString("taskId",""), o.optString("emoji","⏰")); }
        ed.apply();
        call.resolve();
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        JSONArray ids = call.getData().optJSONArray("ids");
        AlarmManager mgr = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        if (ids != null) {
            for (int k = 0; k < ids.length(); k++) {
                mgr.cancel(piFor(getContext(), ids.optInt(k), "", "", "", ""));
            }
        }
        call.resolve();
    }

    @PluginMethod
    public void stopRinging(PluginCall call) {
        Intent i = new Intent(getContext(), AlarmService.class);
        i.setAction("STOP");
        getContext().startService(i);
        call.resolve();
    }

    @PluginMethod
    public void getAction(PluginCall call) {
        String v = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pending", "");
        JSObject r = new JSObject(); r.put("value", v); call.resolve(r);
    }

    @PluginMethod
    public void clearAction(PluginCall call) {
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("pending").apply();
        call.resolve();
    }

    @PluginMethod
    public void setWidget(PluginCall call) {
        String json = call.getString("json", "");
        getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("widget", json).apply();
        TodayWidget.refreshAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void setIcon(PluginCall call) {
        String want = call.getString("name", "classic");
        String[] all = {"classic","ocean","lavender","forest","neon"};
        android.content.pm.PackageManager pm = getContext().getPackageManager();
        String pkg = getContext().getPackageName();
        try {
            for (String n : all) {
                String cls = pkg + ".Icon" + Character.toUpperCase(n.charAt(0)) + n.substring(1);
                int state = n.equals(want) ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                pm.setComponentEnabledSetting(new android.content.ComponentName(pkg, cls), state, android.content.pm.PackageManager.DONT_KILL_APP);
            }
            getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("icon", want).apply();
            call.resolve();
        } catch (Exception e) { call.reject("icon change failed: " + e.getMessage()); }
    }

    @PluginMethod
    public void openChannelSettings(PluginCall call) {
        try {
            String ch = call.getString("channel", "");
            Intent i = new Intent(Build.VERSION.SDK_INT >= 26 ? (ch.isEmpty() ? android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS : android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) : android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
            if (!ch.isEmpty()) i.putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, ch);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Exception ignored) {}
        call.resolve();
    }

    @PluginMethod
    public void canExact(PluginCall call) {
        boolean ok = true;
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager mgr = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
            ok = mgr.canScheduleExactAlarms();
        }
        JSObject r = new JSObject(); r.put("granted", ok); call.resolve(r);
    }

    @PluginMethod
    public void openExactSettings(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                i.setData(Uri.parse("package:" + getContext().getPackageName()));
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            }
        } catch (Exception ignored) {}
        call.resolve();
    }
}
