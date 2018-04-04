package com.mycelo.checkintracker;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.internal.BottomNavigationMenu;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

public class MainActivity extends AppCompatActivity {

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;

    private BottomNavigationView mNavigation;

    private FrameLayout mFrameHome;
    private FrameLayout mFrameDashboard;
    private FrameLayout mFrameNotifications;

    private Button mButtonHome;
    private TextView mTextHome;

    private TextView mTextLastgps;
    private TextView mTextCheckin;
    private TextView mTextCheckout;

    private Switch mSwitchTracking;
    private TextView mTextGpsLog;

    private Context baseContext;
    private SharedPreferences sharedPreferences;
    private MyLocationListener myLocationListener;
    private DatabaseHelper databaseHelper;

    private Boolean firstScreenUpdate;
    private Boolean homeUpdated;
    private Boolean trackingWasEnabled;

    private int lastgpsUpdate;
    private int checkinUpdate;
    private int checkoutUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        baseContext = getBaseContext();

        sharedPreferences = this.getSharedPreferences("com.mycelo.checkintracker.prefs", Context.MODE_PRIVATE);
        databaseHelper = new DatabaseHelper(baseContext);
        LocalBroadcastManager.getInstance(MainActivity.this).registerReceiver(mMessageReceiver, new IntentFilter("gps_event"));
        myLocationListener = new MyLocationListener(baseContext, 20d, new Callable<Integer>() {
            public Integer call() {
                LocationListenerDone();
                return 0;
            }
        });

        mNavigation = (BottomNavigationView) findViewById(R.id.navigation);
        mNavigation.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);

        mFrameHome = (FrameLayout) findViewById(R.id.frame_home);
        mFrameDashboard = (FrameLayout) findViewById(R.id.frame_dashboard);
        mFrameNotifications = (FrameLayout) findViewById(R.id.frame_notifications);

        mButtonHome = (Button) findViewById(R.id.button_home);
        mTextHome = (TextView) findViewById(R.id.text_home);

        mTextLastgps = (TextView) findViewById(R.id.text_lastgps);
        mTextCheckin = (TextView) findViewById(R.id.text_checkin);
        mTextCheckout = (TextView) findViewById(R.id.text_checkout);

        mSwitchTracking = (Switch) findViewById(R.id.switch_tracking);
        mTextGpsLog = (TextView) findViewById(R.id.text_gpslog);

        if (sharedPreferences.getBoolean("homeIsSet", false)) {
            mNavigation.setSelectedItemId(R.id.navigation_dashboard);
            mSwitchTracking.setChecked(sharedPreferences.getBoolean("currentlyTracking", false));
        } else {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("currentlyTracking", false);
            editor.apply();

            mNavigation.setSelectedItemId(R.id.navigation_home);
            mSwitchTracking.setChecked(false);
            mSwitchTracking.setEnabled(false);
        }

        firstScreenUpdate = true;
        homeUpdated = false;
        trackingWasEnabled = false;

        lastgpsUpdate = 0;
        checkinUpdate = 0;
        checkoutUpdate = 0;

        mButtonHome.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                HomeButtonClick();
                return true;
            }
        });

        mSwitchTracking.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SwitchTrackingClick();
            }
        });

        final Handler handlerScreenUpdate = new Handler();
        handlerScreenUpdate.postDelayed(new Runnable() {
            @Override
            public void run() {
                ScreenUpdate();
                handlerScreenUpdate.postDelayed(this, 1500);
            }
        }, 1500);

        updateGpsLog();
        checkLocationPermission();
    }

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem item) {
            switch (item.getItemId()) {
                case R.id.navigation_home:
                    mFrameHome.setVisibility(View.VISIBLE);
                    mFrameDashboard.setVisibility(View.INVISIBLE);
                    mFrameNotifications.setVisibility(View.INVISIBLE);
                    return true;
                case R.id.navigation_dashboard:
                    mFrameHome.setVisibility(View.INVISIBLE);
                    mFrameDashboard.setVisibility(View.VISIBLE);
                    mFrameNotifications.setVisibility(View.INVISIBLE);
                    return true;
                case R.id.navigation_notifications:
                    mFrameHome.setVisibility(View.INVISIBLE);
                    mFrameDashboard.setVisibility(View.INVISIBLE);
                    mFrameNotifications.setVisibility(View.VISIBLE);
                    return true;
            }
            return false;
        }
    };

    private void HomeButtonClick() {
        trackingWasEnabled = mSwitchTracking.isChecked();
        mButtonHome.setEnabled(false);
        mSwitchTracking.setEnabled(false);
        mSwitchTracking.setChecked(false);
        SwitchTrackingClick();
        myLocationListener.startTracking();
    }

    private void SwitchTrackingClick() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (mSwitchTracking.isChecked()) {
            AlarmManager alarmManager = (AlarmManager) baseContext.getSystemService(Context.ALARM_SERVICE);
            Intent gpsTrackerIntent = new Intent(baseContext, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(baseContext, 0, gpsTrackerIntent, 0);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 5000, pendingIntent);
            editor.putBoolean("currentlyTracking", true);
        } else {
            Intent gpsTrackerIntent = new Intent(baseContext, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(baseContext, 0, gpsTrackerIntent, 0);
            AlarmManager alarmManager = (AlarmManager) baseContext.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
            editor.putBoolean("currentlyTracking", false);
        }
        editor.apply();
    }

    private void LocationListenerDone() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("homeIsSet", true);
        editor.putFloat("homeLatitude", myLocationListener.Latitude.floatValue());
        editor.putFloat("homeLongitude", myLocationListener.Longitude.floatValue());
        editor.apply();
        mSwitchTracking.setEnabled(true);

        if (trackingWasEnabled) {
            mSwitchTracking.setChecked(true);
            SwitchTrackingClick();
        }

        homeUpdated = true;
    }

    private void ScreenUpdate() {

        if (homeUpdated || firstScreenUpdate) {
            if (sharedPreferences.getBoolean("homeIsSet", false)) {

                float latitude = sharedPreferences.getFloat("homeLatitude", 0f);
                float longitude = sharedPreferences.getFloat("homeLongitude", 0f);

                mTextHome.setText(String.format(Locale.US, "N/S %f°, W/L %f°%n%n%s",
                        latitude,
                        longitude,
                        getCompleteAddress(latitude, longitude)));
            } else {
                mTextHome.setText(getResources().getString(R.string.advice_homenotset));
            }
            mButtonHome.setEnabled(true);
            homeUpdated = false;
        }

        if (sharedPreferences.getBoolean("homeIsSet", false)) {
            float homeLatitude = sharedPreferences.getFloat("homeLatitude", 0f);
            float homeLongitude = sharedPreferences.getFloat("homeLongitude", 0f);

            Location homeLocation = new Location("");
            homeLocation.setLatitude(homeLatitude);
            homeLocation.setLongitude(homeLongitude);

            if (sharedPreferences.getInt("lastgpsUpdate", 0) != lastgpsUpdate) {
                float lastgpsLatitude = sharedPreferences.getFloat("lastgpsLatitude", 0f);
                float lastgpsLongitude = sharedPreferences.getFloat("lastgpsLongitude", 0f);
                Location lastgpsLocation = new Location("");
                lastgpsLocation.setLatitude(lastgpsLatitude);
                lastgpsLocation.setLongitude(lastgpsLongitude);

                mTextLastgps.setText(buildLocationBox(lastgpsLatitude,
                        lastgpsLongitude,
                        lastgpsLocation.distanceTo(homeLocation),
                        sharedPreferences.getString("lastgpsDate", "")));
            }

            if (sharedPreferences.getInt("checkinUpdate", 0) != checkinUpdate) {
                float checkinLatitude = sharedPreferences.getFloat("checkinLatitude", 0f);
                float checkinLongitude = sharedPreferences.getFloat("checkinLongitude", 0f);
                Location checkinLocation = new Location("");
                checkinLocation.setLatitude(checkinLatitude);
                checkinLocation.setLongitude(checkinLongitude);

                mTextCheckin.setText(buildLocationBox(checkinLatitude,
                        checkinLongitude,
                        checkinLocation.distanceTo(homeLocation),
                        sharedPreferences.getString("checkinDate", "")));
            }

            if (sharedPreferences.getInt("checkoutUpdate", 0) != checkoutUpdate) {
                float checkoutLatitude = sharedPreferences.getFloat("checkoutLatitude", 0f);
                float checkoutLongitude = sharedPreferences.getFloat("checkoutLongitude", 0f);
                Location checkoutLocation = new Location("");
                checkoutLocation.setLatitude(checkoutLatitude);
                checkoutLocation.setLongitude(checkoutLongitude);

                mTextCheckout.setText(buildLocationBox(checkoutLatitude,
                        checkoutLongitude,
                        checkoutLocation.distanceTo(homeLocation),
                        sharedPreferences.getString("checkoutDate", "")));
            }
        }

        firstScreenUpdate = false;
    }

    private String buildLocationBox(float latitude, float longitude, float distance, String date) {
        return String.format(Locale.US, "%s%nN/S %f°, W/L %f°%n%.2f meters from home%n%s",
                date,
                latitude,
                longitude,
                distance,
                getCompleteAddress(latitude, longitude)
        );
    }

    private String getCompleteAddress(double LATITUDE, double LONGITUDE) {

        String result;
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        try {
            List<Address> addresses = geocoder.getFromLocation(LATITUDE, LONGITUDE, 1);
            if (addresses != null) {
                Address returnedAddress = addresses.get(0);
                StringBuilder strReturnedAddress = new StringBuilder("");
                for (int i = 0; i <= returnedAddress.getMaxAddressLineIndex(); i++) {
                    strReturnedAddress.append(returnedAddress.getAddressLine(i)).append("\n");
                }
                result = strReturnedAddress.toString();
            } else {
                result = "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = "";
        }

        return result;
    }

    private void updateGpsLog() {
        ArrayList<String> gps_log = databaseHelper.listLog();
        for (int index = 0; index < gps_log.size(); index++) {
            mTextGpsLog.append(gps_log.get(index));
        }
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            StringBuilder text = new StringBuilder();
            text.append(String.format("%s  %s  N/W %10s°  W/L %10s°  %10s (m)%n",
                    intent.getStringExtra("event"),
                    intent.getStringExtra("date"),
                    intent.getStringExtra("latitude"),
                    intent.getStringExtra("longitude"),
                    intent.getStringExtra("distance")));
            text.append(mTextGpsLog.getText());
            mTextGpsLog.setText(text.toString());
        }
    };

    private void checkLocationPermission() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    MY_PERMISSIONS_REQUEST_LOCATION);

        }
    }
}
