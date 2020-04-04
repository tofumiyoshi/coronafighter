package com.fumi.coronafighter;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class TracingIntentService extends IntentService {
    private static final String ACTION_START = "com.fumi.coronafighter.action.START";

    private static final String PARAM_TIME_INERVAL = "com.fumi.coronafighter.param.TIME_INTERVAL";
    private static final String PARAM_MIN_DISTANCE = "com.fumi.coronafighter.param.MIN_DISTANCE";

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirebaseFirestore;

    private volatile Looper mTracingLooper;

    public TracingIntentService() {
        super("TracingIntentService");
    }

    @Override
    public void onCreate() {
        mAuth = FirebaseAuth.getInstance();
        mFirebaseFirestore = FirebaseFirestore.getInstance();

        HandlerThread thread = new HandlerThread(TracingIntentService.class.getName());
        thread.start();
        mTracingLooper = thread.getLooper();

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(180 * 1000);
        //locationRequest.setSmallestDisplacement(15);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

                        CoronaFighterApplication app = (CoronaFighterApplication)getApplication();
                        app.setCurrentPosition(latLng);

                        if (mAuth.getCurrentUser() != null) {
                            traceUserInFireStore(location, mAuth.getCurrentUser());
                        }
                    }
                }
            }
        };

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void startActionTracing(Context context, int timeInterval, int minDistance) {
        Intent intent = new Intent(context, TracingIntentService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(PARAM_TIME_INERVAL, timeInterval);
        intent.putExtra(PARAM_MIN_DISTANCE, minDistance);

        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_START.equals(action)) {
                int timeInterval = intent.getIntExtra(PARAM_TIME_INERVAL, 180);
                int minDistance = intent.getIntExtra(PARAM_MIN_DISTANCE, 15);

                handleActionStart(timeInterval, minDistance);
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionStart(int timeInterval, int minDistance) {
        locationRequest.setInterval(timeInterval * 1000);
        //locationRequest.setSmallestDisplacement(minDistance);

        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mTracingLooper);
        //mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    private void traceUserInFireStore(Location location, FirebaseUser currentUser) {
        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        activityInfo.put("timestamp", Calendar.getInstance().getTime());
        activityInfo.put("latitude", location.getLatitude());
        activityInfo.put("longitude", location.getLongitude());

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection(currentUser.getEmail())
                .add(activityInfo)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d("firbase-store", "DocumentSnapshot added with ID: " + documentReference.getId());
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w("firbase-store", "Error adding document", e);
                    }
                });
    }
}
