package com.fumi.coronafighter;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LocationService extends Service {

    private LocationManager locationManager;
    private Context context;

    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirebaseFirestore;

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private volatile Looper mTracingLooper;

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();

        mAuth = FirebaseAuth.getInstance();
        mFirebaseFirestore = FirebaseFirestore.getInstance();

        HandlerThread thread = new HandlerThread(LocationService.class.getName());
        thread.start();
        mTracingLooper = thread.getLooper();

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(SettingInfos.tracing_time_interval_second*1000);
        locationRequest.setSmallestDisplacement(SettingInfos.tracing_min_distance_meter);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                CoronaFighterApplication app = (CoronaFighterApplication)getApplication();
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        if (mAuth.getCurrentUser() != null) {
                            traceUserInFireStore(location, mAuth.getCurrentUser());
                        }
                        app.setCurrentPosition(location);
                    }
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        int requestCode = 0;
        String channelId = "default";
        String title = context.getString(R.string.title_activity_maps);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(context, requestCode,
                        intent, PendingIntent.FLAG_UPDATE_CURRENT);

        // ForegroundにするためNotificationが必要、Contextを設定
        NotificationManager notificationManager =
                (NotificationManager)context.
                        getSystemService(Context.NOTIFICATION_SERVICE);

        // Notification　Channel 設定
        NotificationChannel channel = new NotificationChannel(
                channelId, title , NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Silent Notification");
        // 通知音を消さないと毎回通知音が出てしまう
        // この辺りの設定はcleanにしてから変更
        channel.setSound(null,null);
        // 通知ランプを消す
        channel.enableLights(false);
        channel.setLightColor(Color.BLUE);
        // 通知バイブレーション無し
        channel.enableVibration(false);

        if(notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(context, channelId)
                    .setContentTitle(title)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentText("GPS")
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .build();

            // startForeground
            startForeground(1, notification);
        }

        startGPS();

        return START_NOT_STICKY;
    }

    protected void startGPS() {
        StringBuilder strBuf = new StringBuilder();
        strBuf.append("startGPS\n");

        locationRequest.setInterval(SettingInfos.tracing_time_interval_second * 1000);
        locationRequest.setSmallestDisplacement(SettingInfos.tracing_min_distance_meter);

        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mTracingLooper);
    }

    private void stopGPS(){
        mFusedLocationClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopGPS();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void traceUserInFireStore(Location location, FirebaseUser currentUser) {
        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        Date date1 = Calendar.getInstance().getTime();
        Timestamp timestamp = new Timestamp(date1);

        activityInfo.put("timestamp", timestamp);
        activityInfo.put("latitude", location.getLatitude());
        activityInfo.put("longitude", location.getLongitude());

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection(currentUser.getEmail())
                .document(Constants.DATE_FORMAT_4_NAME.format(timestamp.toDate()))
                .set(activityInfo)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d("firebase-store", "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("firebase-store", "Error writing document", e);
                    }
                });
    }
}