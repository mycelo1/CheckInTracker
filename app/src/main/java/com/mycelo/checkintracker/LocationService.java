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

    private static final float CHECKIN_DISTANCE = 30f;
    private static final float CHECKOUT_DISTANCE = 100f;

    private static final float MIN_DISTANCE = 2000f;
    private static final Integer MIN_INTERVAL = 60000;
    private static final float MIN_ACCURACY = 20f;

    private static final float MID_DISTANCE = 10000f;
    private static final Integer MID_INTERVAL = 300000;
    private static final float MID_ACCURACY = 500f;

    private static final float MAX_DISTANCE = 100000f;
    private static final Integer MAX_INTERVAL = 1800000;
    private static final float MAX_ACCURACY = 1000f;

    private static final Integer SLEEP_INTERVAL = 3600000;
    private static final float SLEEP_ACCURACY = 1000f;

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

        double current_accuracy = sharedPreferences.getFloat("currentAccuracy", MIN_ACCURACY);

        myLocationListener = new MyLocationListener(this, current_accuracy, new Callable<Integer>() {
            public Integer call() {
                LocationListenerDone();
                return 0;
            }
        });

        super.onCreate();
    }

    private void LocationListenerDone() {
        Integer interval = processLocation(myLocationListener.CurrentLocation);
        setNextAlarm(interval);
        currentlyProcessingLocation = false;
    }

    private Integer processLocation(Location location) {

        Integer current_interval;
        String event = "G";

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        dateFormat.setTimeZone(TimeZone.getDefault());
        Date date = new Date(location.getTime());

        float latitude = (float) location.getLatitude();
        float longitude = (float) location.getLongitude();

        Location homeLocation = new Location("");
        homeLocation.setLatitude(sharedPreferences.getFloat("homeLatitude", 0));
        homeLocation.setLongitude(sharedPreferences.getFloat("homeLongitude", 0));

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("lastgpsDate", dateFormat.format(date));
        editor.putFloat("lastgpsLatitude", latitude);
        editor.putFloat("lastgpsLongitude", longitude);
        editor.putInt("lastgpsUpdate", random.nextInt());

        if (sharedPreferences.getBoolean("atHome", false)) {
            if (location.distanceTo(homeLocation) > CHECKOUT_DISTANCE) {
                editor.putFloat("checkoutLatitude", latitude);
                editor.putFloat("checkoutLongitude", longitude);
                editor.putString("checkoutDate", dateFormat.format(date));
                editor.putInt("checkoutUpdate", random.nextInt());
                editor.putBoolean("atHome", false);
                editor.putFloat("currentAccuracy", MIN_ACCURACY);
                current_interval = MAX_INTERVAL;
                event = "O";
            } else {
                editor.putFloat("currentAccuracy", MIN_ACCURACY);
                current_interval = MIN_INTERVAL;
            }
        } else {
            if (location.distanceTo(homeLocation) < CHECKIN_DISTANCE) {
                editor.putFloat("checkinLatitude", latitude);
                editor.putFloat("checkinLongitude", longitude);
                editor.putString("checkinDate", dateFormat.format(date));
                editor.putInt("checkinUpdate", random.nextInt());
                editor.putBoolean("atHome", true);
                editor.putFloat("currentAccuracy", MIN_ACCURACY);
                current_interval = MAX_INTERVAL;
                event = "I";
            } else {
                if (location.distanceTo(homeLocation) < MIN_DISTANCE) {
                    editor.putFloat("currentAccuracy", MIN_ACCURACY);
                    current_interval = MIN_INTERVAL;
                } else if (location.distanceTo(homeLocation) < MID_DISTANCE) {
                    editor.putFloat("currentAccuracy", MID_ACCURACY);
                    current_interval = MID_INTERVAL;
                } else if (location.distanceTo(homeLocation) < MAX_DISTANCE) {
                    editor.putFloat("currentAccuracy", MAX_ACCURACY);
                    current_interval = MAX_INTERVAL;
                } else {
                    editor.putFloat("currentAccuracy", SLEEP_ACCURACY);
                    current_interval = SLEEP_INTERVAL;
                }
            }
        }

        editor.apply();
        databaseHelper.insertLog(dateFormat.format(date), latitude, longitude, location.distanceTo(homeLocation), event);
        sendMessageToActivity(dateFormat.format(date), latitude, longitude, location.distanceTo(homeLocation), event);
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
