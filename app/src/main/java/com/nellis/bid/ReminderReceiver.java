package com.nellis.bid;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Schedules + fires "auction ending soon" reminders for watched items. */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "bid_reminders";

    private static long leadMs(Context ctx) {
        int mins = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("reminder_minutes", 10);
        return mins * 60 * 1000L;
    }

    public static void schedule(Context ctx, Item item) {
        long at = item.closeTime - leadMs(ctx);
        if (at <= System.currentTimeMillis()) return;
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.putExtra("title", item.title);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, item.id.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancel(Context ctx, String id) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, id.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String title = intent.getStringExtra("title");
        if (title == null || title.isEmpty()) title = "Your watched item";
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Bid reminders", NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Auction ending soon")
                .setContentText(title + " — ends in ~10 minutes")
                .setAutoCancel(true)
                .build();
        nm.notify((int) (System.currentTimeMillis() % Integer.MAX_VALUE), n);
    }
}
