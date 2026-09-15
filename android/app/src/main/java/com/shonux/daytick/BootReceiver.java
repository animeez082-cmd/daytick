package com.shonux.daytick;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

/** Re-registers pending alarms after a reboot (they are lost by Android otherwise). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            String json = ctx.getSharedPreferences(DayAlarmPlugin.PREFS, Context.MODE_PRIVATE).getString("alarms", "[]");
            JSONArray arr = new JSONArray(json);
            long now = System.currentTimeMillis();
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.optJSONObject(k);
                if (o == null) continue;
                long at = o.optLong("at");
                if (at < now - 60000) continue; // already in the past
                DayAlarmPlugin.scheduleOne(ctx, o.optInt("id"), at, o.optString("title", "Reminder"),
                        o.optString("taskId", ""), o.optString("day", ""), o.optString("kind", "alarm"));
            }
        } catch (Exception ignored) {}
    }
}
