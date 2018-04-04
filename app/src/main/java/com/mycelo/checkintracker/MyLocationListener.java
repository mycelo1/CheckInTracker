package com.mycelo.checkintracker;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.concurrent.Callable;

public class MyLocationListener implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;
    private Context context;
    private Double accuracy;
    private Callable<Integer> func_done;

    public Double Latitude;
    public Double Longitude;
    public Location CurrentLocation;

    MyLocationListener(Context CONTEXT, Double ACCURACY, Callable<Integer> FUNC_DONE) {

        context = CONTEXT;
        accuracy = ACCURACY;
        Latitude = 0d;
        Longitude = 0d;
        func_done = FUNC_DONE;
    }

    public void startTracking() {

        googleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        if (!googleApiClient.isConnected() || !googleApiClient.isConnecting()) {
            googleApiClient.connect();
        }
    }

    public void stopTracking() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    @Override
    public void onLocationChanged(Location location) {

        if (location.getAccuracy() <= accuracy) {
            stopTracking();

            Latitude = location.getLatitude();
            Longitude = location.getLongitude();
            CurrentLocation = location;

            try {
                func_done.call();
            } catch (java.lang.Exception e) {
            }
        }
    }

    @Override
    public void onConnected(Bundle bundle) {

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
        } catch (SecurityException se) {
            se.printStackTrace();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        stopTracking();
    }

    @Override
    public void onConnectionSuspended(int i) {
    }
}
