package com.mycelo.checkintracker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
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

    private ScrollView mScrollLastgps;
    private ScrollView mScrollCheckin;
    private ScrollView mScrollCheckout;

    private Switch mSwitchTracking;

    private EditText mEditCheckInDistance;
    private EditText mEditCheckOutDistance;

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

    private String lastgpsDate;
    private String checkinDate;
    private String checkoutDate;

    @SuppressLint("ClickableViewAccessibility")
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

        mScrollLastgps = (ScrollView) findViewById(R.id.scroll_lastgps);
        mScrollCheckin = (ScrollView) findViewById(R.id.scroll_checkin);
        mScrollCheckout = (ScrollView) findViewById(R.id.scroll_checkout);

        mSwitchTracking = (Switch) findViewById(R.id.switch_tracking);

        mEditCheckInDistance = (EditText) findViewById(R.id.input_checkin_distance);
        mEditCheckOutDistance = (EditText) findViewById(R.id.input_checkout_distance);

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

        mEditCheckInDistance.setText(String.format(Locale.US, "%.0f", sharedPreferences.getFloat("checkInDistance", 25)));
        mEditCheckOutDistance.setText(String.format(Locale.US, "%.0f", sharedPreferences.getFloat("checkOutDistance", 100)));

        mEditCheckInDistance.setEnabled(!mSwitchTracking.isChecked());
        mEditCheckOutDistance.setEnabled(!mSwitchTracking.isChecked());

        firstScreenUpdate = true;
        homeUpdated = false;
        trackingWasEnabled = false;

        lastgpsUpdate = 0;
        checkinUpdate = 0;
        checkoutUpdate = 0;

        lastgpsDate = "";
        checkinDate = "";
        checkoutDate = "";

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

        mScrollLastgps.setOnTouchListener(new OnSwipeTouchListener(baseContext) {
            @Override
            public void onSwipeLeft() {
                DatabaseHelper.GpsEvent gps_event = databaseHelper.getNextGps(lastgpsDate);
                if (gps_event != null) {
                    mTextLastgps.setText(makeLocationText(
                            gps_event.date,
                            gps_event.latitude,
                            gps_event.longitude,
                            gps_event.distance,
                            gps_event.address));
                    lastgpsDate = gps_event.date;
                }
            }

            @Override
            public void onSwipeRight() {
                DatabaseHelper.GpsEvent gps_event = databaseHelper.getPreviousGps(lastgpsDate);
                if (gps_event != null) {
                    mTextLastgps.setText(makeLocationText(
                            gps_event.date,
                            gps_event.latitude,
                            gps_event.longitude,
                            gps_event.distance,
                            gps_event.address));
                    lastgpsDate = gps_event.date;
                }
            }
        });

        mScrollCheckin.setOnTouchListener(new OnSwipeTouchListener(baseContext) {
            @Override
            public void onSwipeLeft() {
                DatabaseHelper.GpsEvent gps_event = databaseHelper.getNextGpsEvent("I", checkinDate);
                if (gps_event != null) {
                    mTextCheckin.setText(makeLocationText(
                            gps_event.date,
                            gps_event.latitude,
                            gps_event.longitude,
                            gps_event.distance,
                            gps_event.address));
                    checkinDate = gps_event.date;
                }
            }

            @Override
            public void onSwipeRight() {
                DatabaseHelper.GpsEvent gps_event = databaseHelper.getPreviousGpsEvent("I", checkinDate);
                if (gps_event != null) {
                    mTextCheckin.setText(makeLocationText(
                            gps_event.date,
                            gps_event.latitude,
                            gps_event.longitude,
                            gps_event.distance,
                            gps_event.address));
                    checkinDate = gps_event.date;
                }
            }
        });

        mScrollCheckout.setOnTouchListener(new OnSwipeTouchListener(baseContext) {
            @Override
            public void onSwipeLeft() {
                DatabaseHelper.GpsEvent gps_event = databaseHelper.getNextGpsEvent("O", checkoutDate);
                if (gps_event != null) {
                    mTextCheckout.setText(makeLocationText(
                            gps_event.date,
                            gps_event.latitude,
                            gps_event.longitude,
                            gps_event.distance,
                            gps_event.address));
                    checkoutDate = gps_event.date;
                }
            }

            @Override
            public void onSwipeRight() {
                DatabaseHelper.GpsEvent gps_event = databaseHelper.getPreviousGpsEvent("O", checkoutDate);
                if (gps_event != null) {
                    mTextCheckout.setText(makeLocationText(
                            gps_event.date,
                            gps_event.latitude,
                            gps_event.longitude,
                            gps_event.distance,
                            gps_event.address));
                    checkoutDate = gps_event.date;
                }
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
        DisableTracking();
        myLocationListener.startTracking();
    }

    private void SwitchTrackingClick() {
        mEditCheckInDistance.setEnabled(!mSwitchTracking.isChecked());
        mEditCheckOutDistance.setEnabled(!mSwitchTracking.isChecked());
        if (mSwitchTracking.isChecked()) {
            EnableTracking();
        } else {
            DisableTracking();
        }
    }

    private void EnableTracking() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        try {
            AlarmManager alarmManager = (AlarmManager) baseContext.getSystemService(Context.ALARM_SERVICE);
            Intent gpsTrackerIntent = new Intent(baseContext, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(baseContext, 0, gpsTrackerIntent, 0);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 5000, pendingIntent);
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }

        float checkInDistance = Float.valueOf(mEditCheckInDistance.getText().toString());
        float checkOutDistance = Float.valueOf(mEditCheckOutDistance.getText().toString());

        editor.putFloat("checkInDistance", checkInDistance);
        editor.putFloat("checkOutDistance", checkOutDistance);
        editor.putBoolean("currentlyTracking", true);
        editor.apply();
    }

    private void DisableTracking() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        try {
            Intent gpsTrackerIntent = new Intent(baseContext, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(baseContext, 0, gpsTrackerIntent, 0);
            AlarmManager alarmManager = (AlarmManager) baseContext.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
        } catch (java.lang.Exception e) {
            e.printStackTrace();
        }
        editor.putBoolean("currentlyTracking", false);
        editor.apply();
    }

    private void LocationListenerDone() {
        if (!myLocationListener.Error) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("homeIsSet", true);
            editor.putBoolean("atHome", true);
            editor.putFloat("homeLatitude", myLocationListener.Latitude.floatValue());
            editor.putFloat("homeLongitude", myLocationListener.Longitude.floatValue());
            editor.apply();
            mSwitchTracking.setEnabled(true);

            if (trackingWasEnabled) {
                mSwitchTracking.setChecked(true);
                SwitchTrackingClick();
            }

            homeUpdated = true;
        } else {
            mTextHome.setText(getResources().getString(R.string.advice_homefailed));
        }
    }

    private void ScreenUpdate() {

        if (homeUpdated || firstScreenUpdate) {
            if (sharedPreferences.getBoolean("homeIsSet", false)) {

                float latitude = sharedPreferences.getFloat("homeLatitude", 0f);
                float longitude = sharedPreferences.getFloat("homeLongitude", 0f);

                mTextHome.setText(String.format(Locale.US, "N/S %f°, W/L %f°%n%s",
                        latitude,
                        longitude,
                        databaseHelper.getCompleteAddress(latitude, longitude)));
            } else {
                mTextHome.setText(getResources().getString(R.string.advice_homenotset));
            }
            mButtonHome.setEnabled(true);
            homeUpdated = false;
        }

        if (sharedPreferences.getBoolean("homeIsSet", false)) {
            int _lastgpsUpdate = sharedPreferences.getInt("lastgpsUpdate", 0);
            if (_lastgpsUpdate == 0) {
                mTextLastgps.setText("");
            } else if (_lastgpsUpdate != lastgpsUpdate) {
                DatabaseHelper.GpsEvent gps_event = databaseHelper.getLastGps();
                if (gps_event != null) {
                    mTextLastgps.setText(makeLocationText(
                            gps_event.date,
                            gps_event.latitude,
                            gps_event.longitude,
                            gps_event.distance,
                            gps_event.address));
                    lastgpsUpdate = _lastgpsUpdate;
                    lastgpsDate = gps_event.date;
                }
            }

            int _checkinUpdate = sharedPreferences.getInt("checkinUpdate", 0);
            if (_checkinUpdate == 0) {
                mTextCheckin.setText("");
            } else if (_checkinUpdate != checkinUpdate) {
                DatabaseHelper.GpsEvent gps_event = databaseHelper.getLastGpsEvent("I");
                if (gps_event != null) {
                    mTextCheckin.setText(makeLocationText(
                            gps_event.date,
                            gps_event.latitude,
                            gps_event.longitude,
                            gps_event.distance,
                            gps_event.address));
                    checkinUpdate = _checkinUpdate;
                    checkinDate = gps_event.date;
                }
            }

            int _checkoutUpdate = sharedPreferences.getInt("checkoutUpdate", 0);
            if (_checkoutUpdate == 0) {
                mTextCheckout.setText("");
            } else if (_checkoutUpdate != checkoutUpdate) {
                DatabaseHelper.GpsEvent gps_event = databaseHelper.getLastGpsEvent("O");
                if (gps_event != null) {
                    mTextCheckout.setText(makeLocationText(
                            gps_event.date,
                            gps_event.latitude,
                            gps_event.longitude,
                            gps_event.distance,
                            gps_event.address));
                    checkoutUpdate = _checkoutUpdate;
                    checkoutDate = gps_event.date;
                }
            }
        }

        firstScreenUpdate = false;
    }

    private String makeLocationText(String date, String latitude, String longitude, String distance, String address) {
        return String.format(Locale.US, "%s%nN/S %s°, W/L %s°%n%s meters from home%n%s",
                date,
                latitude,
                longitude,
                distance,
                address
        );
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
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
