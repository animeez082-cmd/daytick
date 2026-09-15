package com.shonux.daytick;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;

@CapacitorPlugin(name = "DayAlarm")
public class DayAlarmPlugin extends Plugin {

    private AlarmManager am() {
        return (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
    }

    private PendingIntent piFor(int id, String title, String taskId, String day) {
        Intent i = new Intent(getContext(), AlarmReceiver.class);
        i.setAction("com.shonux.daytick.RING." + id); // unique so extras aren't reused
        i.putExtra("title", title);
        i.putExtra("taskId", taskId);
        i.putExtra("day", day);
        i.putExtra("reqId", id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(getContext(), id, i, flags);
    }

    @PluginMethod
    public void setAlarms(PluginCall call) {
        JSONArray arr = call.getData().optJSONArray("alarms");
        if (arr == null) { call.resolve(); return; }
        AlarmManager mgr = am();
        for (int k = 0; k < arr.length(); k++) {
            JSObject o = null;
            try { o = JSObject.fromJSONObject(arr.getJSONObject(k)); } catch (Exception e) { continue; }
            int id = o.optInt("id");
            long at = o.optLong("at"); // epoch millis
            String title = o.optString("title", "Reminder");
            String taskId = o.optString("taskId", "");
            String day = o.optString("day", "");
            PendingIntent pi = piFor(id, title, taskId, day);
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
        call.resolve();
    }

    @PluginMethod
    public void cancelAll(PluginCall call) {
        JSONArray ids = call.getData().optJSONArray("ids");
        if (ids != null) {
            for (int k = 0; k < ids.length(); k++) {
                int id = ids.optInt(k);
                am().cancel(piFor(id, "", "", ""));
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
        String v = getContext().getSharedPreferences("daytick_alarm", Context.MODE_PRIVATE)
                .getString("pending", "");
        JSObject r = new JSObject(); r.put("value", v); call.resolve(r);
    }

    @PluginMethod
    public void clearAction(PluginCall call) {
        getContext().getSharedPreferences("daytick_alarm", Context.MODE_PRIVATE)
                .edit().remove("pending").apply();
        call.resolve();
    }

    @PluginMethod
    public void canExact(PluginCall call) {
        boolean ok = true;
        if (Build.VERSION.SDK_INT >= 31) ok = am().canScheduleExactAlarms();
        JSObject r = new JSObject(); r.put("granted", ok); call.resolve(r);
    }
}
