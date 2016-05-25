package com.atakmap.android.UasDJI.notification;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import android.util.Log;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;

import com.atakmap.android.UasDJI.R;

public class NotificationService extends Service {

    private final static String TAG = "NotificationService";

    @Override

    public IBinder onBind(Intent intent) {
        return null;
    }

 

    @Override
    public void onCreate() {
        super.onCreate();


        
        Log.d(TAG, "getting ready to show the notification, can never use notification compat.");

        NotificationManager notificationManager = 
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Intent contentIntent = new Intent();
        PendingIntent appIntent = PendingIntent.getActivity(this, 0, contentIntent, 0);

        Notification notification = 
                new Notification(R.drawable.abc, "Hello World!", System.currentTimeMillis());
        notification.setLatestEventInfo(this, "1", "2", appIntent);

        notificationManager.notify(9999, notification);

    }

}
