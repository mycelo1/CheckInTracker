package com.mycelo.checkintracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Address;
import android.location.Geocoder;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String LOG_FORMAT = "%s  %s  N/W %10s°  W/L %10s°  %10s (m)%n";

    private static final String DATABASE_NAME = "checkintracker.db";
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String MAX_DATE = "9999-99-99 99:99:99";

    private Context baseContext;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, 1);
        baseContext = context;
    }

    public class GpsEvent {
        public String event;
        public String date;
        public String latitude;
        public String longitude;
        public String distance;
        public String address;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE gps_log (date TEXT PRIMARY KEY, event TEXT, latitude TEXT, longitude TEXT, distance TEXT, address TEXT)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //
    }

    public boolean insertLog(String date, float latitude, float longitude, float distance, String event) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, -24 * 7);
        String address = getCompleteAddress(latitude, longitude);

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("date", date);
        contentValues.put("event", event);
        contentValues.put("latitude", formatCoordinate(latitude));
        contentValues.put("longitude", formatCoordinate(longitude));
        contentValues.put("distance", formatDistance(distance));
        contentValues.put("address", address);
        db.insert("gps_log", null, contentValues);
        db.delete("gps_log", "date < ?", new String[]{formatDate(calendar.getTime())});
        return true;
    }

    public ArrayList<String> listLog() {
        ArrayList<String> array_list = new ArrayList<String>();
        SQLiteDatabase database = this.getReadableDatabase();
        Cursor cursor = database.rawQuery("SELECT * FROM gps_log ORDER BY date DESC", null);
        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            array_list.add(String.format(LOG_FORMAT,
                    cursor.getString(cursor.getColumnIndex("event")),
                    cursor.getString(cursor.getColumnIndex("date")),
                    cursor.getString(cursor.getColumnIndex("latitude")),
                    cursor.getString(cursor.getColumnIndex("longitude")),
                    cursor.getString(cursor.getColumnIndex("distance"))));
            cursor.moveToNext();
        }

        cursor.close();
        return array_list;
    }

    public GpsEvent getPreviousGps(String date) {
        SQLiteDatabase database = this.getReadableDatabase();
        Cursor cursor = database.rawQuery(
                "SELECT * FROM gps_log " +
                        "WHERE date < ? " +
                        "ORDER BY date DESC LIMIT 1",
                new String[]{date});
        return getFirstGpsEvent(cursor);
    }

    public GpsEvent getNextGps(String date) {
        SQLiteDatabase database = this.getReadableDatabase();
        Cursor cursor = database.rawQuery(
                "SELECT * FROM gps_log " +
                        "WHERE date > ? " +
                        "ORDER BY date LIMIT 1",
                new String[]{date});
        return getFirstGpsEvent(cursor);
    }

    public GpsEvent getLastGps() {
        return getPreviousGps(MAX_DATE);
    }

    public GpsEvent getPreviousGpsEvent(String event, String date) {
        SQLiteDatabase database = this.getReadableDatabase();
        Cursor cursor = database.rawQuery(
                "SELECT * FROM gps_log " +
                        "WHERE event = ? " +
                        "AND date < ? " +
                        "ORDER BY date DESC LIMIT 1",
                new String[]{event, date});
        return getFirstGpsEvent(cursor);
    }

    public GpsEvent getNextGpsEvent(String event, String date) {
        SQLiteDatabase database = this.getReadableDatabase();
        Cursor cursor = database.rawQuery(
                "SELECT * FROM gps_log " +
                        "WHERE event = ? " +
                        "AND date > ? " +
                        "ORDER BY date LIMIT 1",
                new String[]{event, date});
        return getFirstGpsEvent(cursor);
    }

    public GpsEvent getLastGpsEvent(String event) {
        return getPreviousGpsEvent(event, MAX_DATE);
    }

    public void changeGpsEvent(String date, String event) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("event", event);
        db.update("gps_log", cv, "date = ?", new String[] {date});
    }

    private GpsEvent getFirstGpsEvent(Cursor cursor) {
        cursor.moveToFirst();
        if (!cursor.isAfterLast()) {
            GpsEvent result = new GpsEvent();
            result.date = cursor.getString(cursor.getColumnIndex("date"));
            result.latitude = cursor.getString(cursor.getColumnIndex("latitude"));
            result.longitude = cursor.getString(cursor.getColumnIndex("longitude"));
            result.distance = cursor.getString(cursor.getColumnIndex("distance"));
            result.address = cursor.getString(cursor.getColumnIndex("address"));
            cursor.close();
            return result;
        } else {
            return null;
        }
    }

    public String getCompleteAddress(double LATITUDE, double LONGITUDE) {

        String result;
        Geocoder geocoder = new Geocoder(baseContext, Locale.getDefault());

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
            result = "";
        }

        return result;
    }

    public static String formatDate(Date date) {
        DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        dateFormat.setTimeZone(TimeZone.getDefault());
        return dateFormat.format(date);
    }

    public static String formatCoordinate(float coordinate) {
        return String.format(Locale.US, "%.6f", coordinate);
    }

    public static String formatDistance(float distance) {
        return String.format(Locale.US, "%.2f", distance);
    }
}