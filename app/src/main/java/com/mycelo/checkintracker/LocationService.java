package com.mycelo.checkintracker;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.Callable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class LocationService extends Service {

    private static boolean currentlyProcessingLocation = false;

    private Context baseContext;
    private Random random;
    private SharedPreferences sharedPreferences;
    private MyLocationListener myLocationListener;
    private DatabaseHelper databaseHelper;

    @Override
    public void onCreate() {

        baseContext = getBaseContext();
        random = new Random();
        sharedPreferences = this.getSharedPreferences("com.mycelo.checkintracker.prefs", Context.MODE_PRIVATE);
        databaseHelper = new DatabaseHelper(baseContext);

        double current_accuracy = sharedPreferences.getFloat("currentAccuracy", Constants.MIN_ACCURACY);

        myLocationListener = new MyLocationListener(this, current_accuracy, new Callable<Integer>() {
            public Integer call() {
                LocationListenerDone();
                return 0;
            }
        });

        super.onCreate();
    }

    private void LocationListenerDone() {
        if (!myLocationListener.Error) {
            Integer interval = processLocation(myLocationListener.CurrentLocation);
            setNextAlarm(interval);
        } else {
            setNextAlarm(Constants.MIN_INTERVAL);
        }
        currentlyProcessingLocation = false;
    }

    private Integer processLocation(Location location) {

        Integer current_interval;
        String event = "G";
        Date date = new Date(location.getTime());

        float latitude = (float) location.getLatitude();
        float longitude = (float) location.getLongitude();

        Location homeLocation = new Location("");
        homeLocation.setLatitude(sharedPreferences.getFloat("homeLatitude", 0));
        homeLocation.setLongitude(sharedPreferences.getFloat("homeLongitude", 0));

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("lastgpsUpdate", random.nextInt());

        float checkInDistance = sharedPreferences.getFloat("checkInDistance", 25);
        float checkOutDistance = sharedPreferences.getFloat("checkOutDistance", 100);

        if (sharedPreferences.getBoolean("atHome", false)) {
            if (location.distanceTo(homeLocation) > checkOutDistance) {
                editor.putInt("checkoutUpdate", random.nextInt());
                editor.putBoolean("atHome", false);
                editor.putFloat("currentAccuracy", Constants.MIN_ACCURACY);
                current_interval = Constants.AFTER_CHECK_INTERVAL;
                event = "O";
            } else {
                editor.putFloat("currentAccuracy", Constants.MIN_ACCURACY);
                current_interval = Constants.MIN_INTERVAL;
            }
        } else {
            if (location.distanceTo(homeLocation) < checkInDistance) {
                editor.putInt("checkinUpdate", random.nextInt());
                editor.putBoolean("atHome", true);
                editor.putFloat("currentAccuracy", Constants.MIN_ACCURACY);
                current_interval = Constants.AFTER_CHECK_INTERVAL;
                event = "I";
            } else {
                if (location.distanceTo(homeLocation) < Constants.MIN_DISTANCE) {
                    editor.putFloat("currentAccuracy", Constants.MIN_ACCURACY);
                    current_interval = Constants.MIN_INTERVAL;
                } else if (location.distanceTo(homeLocation) < Constants.MID_DISTANCE) {
                    editor.putFloat("currentAccuracy", Constants.MID_ACCURACY);
                    current_interval = Constants.MID_INTERVAL;
                } else if (location.distanceTo(homeLocation) < Constants.MAX_DISTANCE) {
                    editor.putFloat("currentAccuracy", Constants.MAX_ACCURACY);
                    current_interval = Constants.MAX_INTERVAL;
                } else {
                    editor.putFloat("currentAccuracy", Constants.SLEEP_ACCURACY);
                    current_interval = Constants.SLEEP_INTERVAL;
                }
            }
        }

        databaseHelper.insertLog(DatabaseHelper.formatDate(date), latitude, longitude, location.distanceTo(homeLocation), event);
        sendMessageToActivity(DatabaseHelper.formatDate(date), latitude, longitude, location.distanceTo(homeLocation), event);
        editor.apply();
        return current_interval;
    }

    private void sendMessageToActivity(String date, float latitude, float longitude, float distance, String event) {
        Intent intent = new Intent("gps_event");
        intent.putExtra("date", date);
        intent.putExtra("latitude", String.format(Locale.US, "%.6f", latitude));
        intent.putExtra("longitude", String.format(Locale.US, "%.6f", longitude));
        intent.putExtra("distance", String.format(Locale.US, "%.2f", distance));
        intent.putExtra("event", event);
        LocalBroadcastManager.getInstance(baseContext).sendBroadcast(intent);
    }

    private void setNextAlarm(Integer interval) {
        AlarmManager alarmManager = (AlarmManager) baseContext.getSystemService(Context.ALARM_SERVICE);
        Intent gpsTrackerIntent = new Intent(baseContext, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(baseContext, 0, gpsTrackerIntent, 0);
        Boolean currentlyTracking = sharedPreferences.getBoolean("currentlyTracking", false);

        if (currentlyTracking) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + interval,
                    pendingIntent);
        } else {
            alarmManager.cancel(pendingIntent);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (!currentlyProcessingLocation) {
            currentlyProcessingLocation = true;
            myLocationListener.startTracking();
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
