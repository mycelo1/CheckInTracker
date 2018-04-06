package com.mycelo.checkintracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class UpgradeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Uri packageName = intent.getData();
        if (packageName.toString().equals("package:" + context.getPackageName())) {
            //
        }
    }
}
