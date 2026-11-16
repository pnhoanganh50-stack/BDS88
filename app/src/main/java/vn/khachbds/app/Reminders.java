package vn.khachbds.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Đặt / hủy các thông báo nhắc hẹn bằng AlarmManager. */
final class Reminders {

    static final String CHANNEL_ID = "lich_hen";
    static final String ACTION_REMIND = "vn.khachbds.app.REMIND";
    private static final String PREFS = "reminders";
    private static final int BASE_CODE = 5000;
    private static final int MAX = 60;

    private Reminders() {
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "Lịch hẹn khách hàng", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Nhắc trước giờ hẹn và ngày cần liên hệ lại khách");
                nm.createNotificationChannel(ch);
            }
        }
    }

    static void save(Context c, String json) {
        prefs(c).edit().putString("json", json == null ? "[]" : json).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Intent baseIntent(Context c) {
        Intent it = new Intent(c, ReminderReceiver.class);
        it.setAction(ACTION_REMIND);
        return it;
    }

    /** Hủy toàn bộ nhắc cũ rồi đặt lại theo danh sách đã lưu. */
    static synchronized void scheduleSaved(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        for (int i = 0; i < MAX; i++) {
            PendingIntent old = PendingIntent.getBroadcast(c, BASE_CODE + i, baseIntent(c),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (old != null) {
                am.cancel(old);
                old.cancel();
            }
        }

        JSONArray arr;
        try {
            arr = new JSONArray(prefs(c).getString("json", "[]"));
        } catch (JSONException e) {
            return;
        }

        long now = System.currentTimeMillis();
        int code = 0;
        for (int i = 0; i < arr.length() && code < MAX; i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            long at = o.optLong("at", 0L);
            if (at <= now) continue;

            Intent it = baseIntent(c);
            it.putExtra("title", o.optString("title", "Lịch hẹn"));
            it.putExtra("text", o.optString("text", ""));
            it.putExtra("nid", BASE_CODE + code);
            PendingIntent pi = PendingIntent.getBroadcast(c, BASE_CODE + code, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            setAlarm(am, at, pi);
            code++;
        }
    }

    private static void setAlarm(AlarmManager am, long at, PendingIntent pi) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }
}
