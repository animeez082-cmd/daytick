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
        // persist so BootReceiver can re-register after a restart
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("alarms", arr.toString()).apply();
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
