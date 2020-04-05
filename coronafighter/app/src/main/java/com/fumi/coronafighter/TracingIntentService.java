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
import androidx.annotation.Nullable;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;
import com.google.openlocationcode.OpenLocationCode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
    private static final String ACTION_STOP = "com.fumi.coronafighter.action.STOP";

    private static final String PARAM_TIME_INERVAL = "com.fumi.coronafighter.param.TIME_INTERVAL";
    private static final String PARAM_MIN_DISTANCE = "com.fumi.coronafighter.param.MIN_DISTANCE";

    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirebaseFirestore;

    private volatile Looper mTracingLooper;
    private ListenerRegistration mListenerRegistration;

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
        locationRequest.setSmallestDisplacement(15);

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

    /**
     * Starts this service to perform action Foo with the given parameters. If
     * the service is already performing a task this action will be queued.
     *
     * @see IntentService
     */
    public static void stopActionTracing(Context context, int timeInterval, int minDistance) {
        Intent intent = new Intent(context, TracingIntentService.class);
        intent.setAction(ACTION_STOP);

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
            else if (ACTION_STOP.equals(action)) {
                handleActionStop();
            }
        }
    }

    /**
     * Handle action Start in the provided background thread with the provided
     * parameters.
     */
    private void handleActionStart(int timeInterval, int minDistance) {
        locationRequest.setInterval(timeInterval * 1000);
        locationRequest.setSmallestDisplacement(minDistance);

        mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mTracingLooper);
        //mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    /**
     * Handle action Stop in the provided background thread with the provided
     * parameters.
     */
    private void handleActionStop() {
        mFusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void traceUserInFireStore(Location location, FirebaseUser currentUser) {
        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        Date timestamp = Calendar.getInstance().getTime();
        activityInfo.put("timestamp", timestamp);
        activityInfo.put("latitude", location.getLatitude());
        activityInfo.put("longitude", location.getLongitude());

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection(currentUser.getEmail())
                .document(Constants.DATE_FORMAT_4_NAME.format(timestamp))
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

        OpenLocationCode olc = new OpenLocationCode(location.getLatitude(), location.getLongitude(),
                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
        if (olc == null) {
            return;
        }
        final String LocCode = olc.getCode().substring(0, Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);

        if (mListenerRegistration != null) {
            mListenerRegistration.remove();
        }

        // コードの構成は、地域コード、都市コード、街区コード、建物コードからなります。
        // 例えば8Q7XPQ3C+J88というコードの場合は、8Q7Xが地域コード（100×100km）、
        // PQが都市コード（5×5km）、3Cが街区コード（250×250m）、
        // +以降のJ88は建物コード（14×14m）を意味しています。
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        final Date date1 = cal.getTime();

        final String locCode1 = LocCode.substring(0, Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);
        mListenerRegistration = mFirebaseFirestore.collection("corona-infos")
                .document(locCode1)
                .collection("sub-areas")
                .whereGreaterThan(currentUser.getEmail().replace(".", "-"), date1)
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        CoronaFighterApplication app = (CoronaFighterApplication)getApplication();
                        Collection<WeightedLatLng> alertAreas = new ArrayList<WeightedLatLng>();

                        List<DocumentSnapshot> documents = queryDocumentSnapshots.getDocuments();
                        for(DocumentSnapshot document : documents){
                            String locCode2 = document.getId();
                            String code = locCode1 + locCode2;

                            Map<String, Object> data = document.getData();
                            Iterator<String> i = data.keySet().iterator();
                            int cnt = 0;
                            while(i.hasNext()) {
                                String key = i.next();
                                Timestamp timestamp = (Timestamp)data.get(key);

                                if(timestamp.toDate().after(date1)) {
                                    cnt++;
                                }
                            }

                            if (cnt == 0) {
                                continue;
                            }
                            OpenLocationCode openLocationCode = new OpenLocationCode(code);
                            OpenLocationCode.CodeArea areaCode = openLocationCode.decode();
                            LatLng latlng = new LatLng(areaCode.getCenterLatitude(), areaCode.getCenterLongitude());
                            double intensity = 0.0;
                            if (cnt >= Constants.INFECTION_SATURATION_CNT_MAX) {
                                intensity = 1.0;
                            }
                            else if (cnt <= Constants.INFECTION_SATURATION_CNT_MIN) {
                                intensity = 0.0;
                            }
                            else {
                                intensity = ((double) cnt - Constants.INFECTION_SATURATION_CNT_MIN)/Constants.INFECTION_SATURATION_CNT_MAX;
                            }
                            if (intensity <= 0.0) {
                                continue;
                            }
                            WeightedLatLng value = new WeightedLatLng(latlng, intensity);
                            alertAreas.add(value);
                        }
                        app.setAlertAreas(alertAreas);
                    }
                });
    }
}
