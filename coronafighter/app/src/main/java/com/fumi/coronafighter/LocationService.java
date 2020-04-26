package com.fumi.coronafighter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.fumi.coronafighter.firebase.FireStore;
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
import com.google.firebase.firestore.GeoPoint;
import com.google.openlocationcode.OpenLocationCode;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LocationService extends Service {
    private static final String TAG = "LocationService";

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
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
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
        String title = context.getString(R.string.title_activity_main);

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
                    .setContentText("位置情報追跡")
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

        stopForeground(true);
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
        OpenLocationCode olc = new OpenLocationCode(location.getLatitude(), location.getLongitude(),
                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
        final String locCode = olc.getCode();

        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        Date date1 = Calendar.getInstance().getTime();
        Timestamp timestamp = new Timestamp(date1);

        activityInfo.put("timestamp", timestamp);
        GeoPoint gp = new GeoPoint(location.getLatitude(), location.getLongitude());
        activityInfo.put("location", gp);
        activityInfo.put("locationcode", locCode);

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection("users")
                .document(currentUser.getEmail())
                .collection("trace-infos")
                .document(Constants.DATE_FORMAT_4_NAME.format(timestamp.toDate()))
                .set(activityInfo)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing document", e);
                    }
                });

//        if (FireStore.new_coronavirus_infection_flag == 1) {
//            FireStore.registNewCoronavirusInfo(currentUser, locCode);
//        }
    }
}
