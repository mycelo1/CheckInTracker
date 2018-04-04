package com.mycelo.checkintracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "checkintracker.db";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE gps_log (date TEXT PRIMARY KEY, latitude TEXT, longitude TEXT, distance TEXT, event TEXT)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS gps_log");
        onCreate(db);
    }

    public boolean insertLog(String date, float latitude, float longitude, float distance, String event) {

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        dateFormat.setTimeZone(TimeZone.getDefault());
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, -24);

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("date", date);
        contentValues.put("latitude", String.format(Locale.US, "%.6f", latitude));
        contentValues.put("longitude", String.format(Locale.US, "%.6f", longitude));
        contentValues.put("distance", String.format(Locale.US, "%.2f", distance));
        contentValues.put("event", event);
        db.insert("gps_log", null, contentValues);
        db.delete("gps_log", "date < ?", new String[]{dateFormat.format(calendar.getTime())});
        return true;
    }

    public ArrayList<String> listLog() {

        ArrayList<String> array_list = new ArrayList<String>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor res = db.rawQuery("SELECT * FROM gps_log ORDER BY date DESC", null);
        res.moveToFirst();

        while (!res.isAfterLast()) {
            array_list.add(String.format("%s  %s  N/W %10s°  W/L %10s°  %10s (m)%n",
                    res.getString(res.getColumnIndex("event")),
                    res.getString(res.getColumnIndex("date")),
                    res.getString(res.getColumnIndex("latitude")),
                    res.getString(res.getColumnIndex("longitude")),
                    res.getString(res.getColumnIndex("distance"))));
            res.moveToNext();
        }

        res.close();
        return array_list;
    }
}