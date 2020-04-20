package com.fumi.coronafighter.firebase;

import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fumi.coronafighter.Constants;
import com.fumi.coronafighter.CoronaFighterApplication;
import com.fumi.coronafighter.MapsActivity;
import com.fumi.coronafighter.SettingInfos;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.maps.android.heatmaps.WeightedLatLng;
import com.google.maps.android.projection.SphericalMercatorProjection;
import com.google.openlocationcode.OpenLocationCode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FireStore {
    private static final String TAG = "FireStore";

    private static FirebaseFirestore mFirebaseFirestore;
    private static MapsActivity mActivity;

    private static ListenerRegistration mListenerStatus;
    private static ListenerRegistration mListenerAlert;
    public static int new_coronavirus_infection_flag = 0;
    public static Location currentLocation;
    public static Collection<WeightedLatLng> mAlertAreas = new ArrayList<WeightedLatLng>();

    public static void init(MapsActivity activity) {
        mFirebaseFirestore = FirebaseFirestore.getInstance();

        mActivity = activity;

        mListenerStatus = mFirebaseFirestore.collection(mActivity.mAuth.getCurrentUser().getEmail())
                .whereEqualTo(FieldPath.documentId(),"status")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        for(DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                            new_coronavirus_infection_flag = document.getLong("new_coronavirus_infection_flag").intValue();

                            mActivity.invalidateOptionsMenu();
                            break;
                        }
                    }
                });

        mListenerAlert = mFirebaseFirestore.collection("corona-infos")
                .whereEqualTo(FieldPath.documentId(),"update-info")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            return;
                        }
                        if(currentLocation != null) {
                            refreshAlertAreas(currentLocation);
                        }

                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            SettingInfos.tracing_time_interval_second = doc.getLong("tracing_time_interval_second").intValue();
                            SettingInfos.tracing_min_distance_meter = doc.getLong("tracing_min_distance_meter").intValue();

                            SettingInfos.map_min_zoom = doc.getLong("map_min_zoom").intValue();
                            SettingInfos.map_default_zoom = doc.getLong("map_default_zoom").intValue();

                            SettingInfos.refresh_alarm_distance_min_meter = doc.getLong("refresh_alarm_distance_min_meter").intValue();

                            SettingInfos.refresh_alarm_areas_min_interval_second = doc.getLong("refresh_alarm_areas_min_interval_second").intValue();

                            SettingInfos.alarm_limit = doc.getLong("alarm_limit").intValue();

                            SettingInfos.infection_saturation_cnt_min = doc.getLong("infection_saturation_cnt_min").intValue();
                            SettingInfos.infection_saturation_cnt_max = doc.getLong("infection_saturation_cnt_max").intValue();
                        }
                    }
                });
    }

    public static void registNewCoronavirusInfo(FirebaseUser currentUser, String locCode, Timestamp timestamp) {
        // Create a new user with a first and last name
        Map<String, Object> info = new HashMap<>();
        info.put(currentUser.getEmail().replace(".", "-"), timestamp);

        Map<String, Object> info2 = new HashMap<>();
        info2.put("timestamp", timestamp);

        // Add a new document with a generated ID;
        String locCode1 = locCode.substring(0, Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);
        String locCode2 = locCode.substring(Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);

        mFirebaseFirestore.collection("corona-infos")
                .document(locCode1)
                .set(info2, SetOptions.merge())
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

        mFirebaseFirestore.collection("corona-infos")
                .document(locCode1)
                .collection("sub-areas")
                .document(locCode2)
                .set(info, SetOptions.merge())
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
    }

    public static void removeNewCoronavirusInfo(final FirebaseUser currentUser, final String locCode) {
        final String field_key = currentUser.getEmail().replace(".", "-");

        // Create a new user with a first and last name
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -3600);
        Date date1 = cal.getTime();
        Timestamp timeStart = new Timestamp(date1);

        String locCode1 = locCode;
        if (locCode.length() > 6) {
            locCode1 = locCode.substring(0, 6);
        }

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection("corona-infos")
                .document(locCode1)
                .collection("sub-areas")
                .whereGreaterThan(field_key, timeStart)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Log.d(TAG, document.getId() + " => " + document.getData());
                                String locCode2 = document.getId();

                                if (locCode.length() > 6) {
                                    if (!locCode2.equals(locCode.substring(6))) {
                                        continue;
                                    }
                                }

                                Map<String, Object> info = new HashMap<>();
                                info.put(field_key, FieldValue.delete());

                                mFirebaseFirestore.collection("corona-infos")
                                        .document(locCode.substring(0, 6))
                                        .collection("sub-areas")
                                        .document(locCode2)
                                        .update(info)
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

                            }
                        } else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }
                    }
                });
    }

    public static void reportNewCoronavirusInfection(final FirebaseUser currentUser, int new_coronavirus_infection_flag) {
        mListenerAlert.remove();

        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        activityInfo.put("timestamp", Constants.DATE_FORMAT.format(Calendar.getInstance().getTime()));
        activityInfo.put("new_coronavirus_infection_flag", new_coronavirus_infection_flag);

        // Add a new document with a generated ID;
        mFirebaseFirestore.collection(currentUser.getEmail())
                .document("status")
                .set(activityInfo, SetOptions.merge())
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

        // 警報基準
        // 日時：　過去７日間
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1 * SettingInfos.alarm_limit);
        final Date date1 = cal.getTime();
        Timestamp timestamp = new Timestamp(date1);

        if (new_coronavirus_infection_flag == 1) {
            mFirebaseFirestore.collection(currentUser.getEmail()).whereGreaterThan("timestamp", timestamp)
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if (task.isSuccessful()) {
                                QuerySnapshot snapshot = task.getResult();
                                for(DocumentSnapshot doc: snapshot.getDocuments()){
                                    double latitude = doc.getDouble("latitude");
                                    double longitude = doc.getDouble("longitude");
                                    Timestamp timestamp = doc.getTimestamp("timestamp");

                                    OpenLocationCode olc = new OpenLocationCode(latitude, longitude,
                                            Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                                    String locCode = olc.getCode();
                                    registNewCoronavirusInfo(currentUser, locCode, timestamp);
                                }
                            } else {
                                Log.d(TAG, "get failed with ", task.getException());
                            }
                        }
                    });
        } else {
            mFirebaseFirestore.collection("corona-infos")
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                            if (task.isSuccessful()) {
                                QuerySnapshot snapshot = task.getResult();
                                List<DocumentSnapshot> list = snapshot.getDocuments();
                                for(DocumentSnapshot doc: list){
                                    String docId = doc.getId();
                                    if (docId.equals("update-info")) {
                                        continue;
                                    }

                                    //Log.d("firebase-store", "locCode = " + locCode);
                                    removeNewCoronavirusInfo(currentUser, docId);
                                }
                            } else {
                                Log.d(TAG, "get failed with ", task.getException());
                            }
                        }
                    });
        }

        reportNewCoronavirusInfectionComlete(timestamp);
    }

    private static void reportNewCoronavirusInfectionComlete(Timestamp timestamp) {
        mListenerAlert = mFirebaseFirestore.collection("corona-infos")
                .whereEqualTo(FieldPath.documentId(),"update-info")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if(currentLocation != null) {
                            refreshAlertAreas(currentLocation);
                        }
                    }
                });

        Map<String, Object> info2 = new HashMap<>();
        info2.put("timestamp", timestamp);
        mFirebaseFirestore.collection("corona-infos")
                .document("update-info")
                .set(info2, SetOptions.merge())
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
    }

    public static void refreshAlertAreas(Location location) {
        if (location == null) {
            return;
        }

        ArrayList<String> locCodes = new ArrayList<String>();

        OpenLocationCode olc = new OpenLocationCode(location.getLatitude(), location.getLongitude(),
                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
        String locCode = olc.getCode();
        locCodes.add(locCode);

        refreshAlartAreas(locCodes);
    }

    public static void refreshAlartAreas(ArrayList<String> locCodes) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, -1 * SettingInfos.refresh_alarm_areas_min_interval_second);
        Date now = Calendar.getInstance().getTime();

        if (mActivity.refreshAlarmAreasTime != null && mActivity.refreshAlarmAreasTime.after(now)) {
            return;
        }

        mAlertAreas.clear();
        // コードの構成は、地域コード、都市コード、街区コード、建物コードからなります。
        // 例えば8Q7XPQ3C+J88というコードの場合は、8Q7Xが地域コード（100×100km）、
        // PQが都市コード（5×5km）、3Cが街区コード（250×250m）、
        // +以降のJ88は建物コード（14×14m）を意味しています。
        mActivity.setHeatMap(mAlertAreas);

        ArrayList<String> locCode1s = new ArrayList<String>();
        for(String locCode : locCodes){
            String locCode1 = locCode.substring(0, Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);
            if (!locCode1s.contains(locCode1)) {
                locCode1s.add(locCode1);
            }
        }

        mFirebaseFirestore.collection("corona-infos")
                .whereIn(FieldPath.documentId(), locCode1s)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                final String locCode1 = document.getId();

                                document.getReference()
                                        .collection("sub-areas")
                                        .get()
                                        .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                            @Override
                                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                                if (task.isSuccessful()) {
                                                    CoronaFighterApplication app = (CoronaFighterApplication)mActivity.getApplication();

                                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                                        Log.d(TAG, document.getId() + " => " + document.getData());

                                                        String locCode2 = document.getId();
                                                        String code = locCode1 + locCode2;

                                                        Map<String, Object> data = document.getData();
                                                        addAlertAreas(code, data, mAlertAreas);
                                                    }

                                                    mActivity.setHeatMap(mAlertAreas);

                                                } else {
                                                    Log.d(TAG, "Error getting documents: ", task.getException());
                                                }
                                            }
                                        });
                            }
                        }
                        else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }
                    }
                });
    }

    public static void addAlertAreas(String code, Map<String, Object> data, Collection<WeightedLatLng> alertAreas) {
        // 警報基準
        // 日時：　過去７日間
        // エリア：　PQが都市コード（5×5km）
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -7);
        final Date date1 = cal.getTime();

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
            return;
        }
        OpenLocationCode openLocationCode = new OpenLocationCode(code);
        OpenLocationCode.CodeArea areaCode = openLocationCode.decode();
        LatLng latlng = new LatLng(areaCode.getCenterLatitude(), areaCode.getCenterLongitude());
        double intensity = 0.0;
        if (cnt >= SettingInfos.infection_saturation_cnt_max) {
            intensity = 1.0;
        }
        else if (cnt <= SettingInfos.infection_saturation_cnt_min) {
            intensity = 0.0;
        }
        else {
            intensity = ((double) cnt - SettingInfos.infection_saturation_cnt_min)/
                    (SettingInfos.infection_saturation_cnt_max- SettingInfos.infection_saturation_cnt_min);
        }
        if (intensity <= 0.0) {
            return;
        }
        WeightedLatLng value = new WeightedLatLng(latlng, intensity);
        alertAreas.add(value);
    }

    public static List<String> findExistInAlertAreasCode(final LatLng latLng) {
        if  (mAlertAreas == null || mAlertAreas.size() == 0) {
            return null;
        }

        List<String> res = new ArrayList<String>();

        OpenLocationCode olc = new OpenLocationCode(latLng.latitude, latLng.longitude,
                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
        String code = olc.getCode();

        SphericalMercatorProjection sProjection = new SphericalMercatorProjection(1.0D);
        for (WeightedLatLng item : mAlertAreas) {
            LatLng latLng2 = sProjection.toLatLng(item.getPoint());
            OpenLocationCode olc2 = new OpenLocationCode(latLng2.latitude, latLng2.longitude,
                    Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
            String code2 = olc2.getCode();

            if (code2.startsWith(code.substring(0, 8))) {
                res.add(code2);
            }
        }

        return res;
    }
}
