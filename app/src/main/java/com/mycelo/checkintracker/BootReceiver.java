package com.mycelo.checkintracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent gpsTrackerIntent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, gpsTrackerIntent, 0);

        SharedPreferences sharedPreferences = context.getSharedPreferences("com.mycelo.checkintracker.prefs", Context.MODE_PRIVATE);
        Boolean currentlyTracking = sharedPreferences.getBoolean("currentlyTracking", false);

        if (currentlyTracking) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    0,
                    pendingIntent);
        } else {
            alarmManager.cancel(pendingIntent);
        }
    }
}
