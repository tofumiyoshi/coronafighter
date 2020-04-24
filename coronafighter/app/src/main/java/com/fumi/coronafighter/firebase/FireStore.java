package com.fumi.coronafighter.firebase;

import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.fumi.coronafighter.AlarmInfo;
import com.fumi.coronafighter.Constants;
import com.fumi.coronafighter.CurrentPositionViewModel;
import com.fumi.coronafighter.MainActivity;
import com.fumi.coronafighter.SettingInfos;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
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
import com.google.firebase.firestore.WriteBatch;
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
import java.util.concurrent.ExecutionException;

public class FireStore {
    private static final String TAG = "FireStore";

    private static FirebaseFirestore mFirebaseFirestore;
    public static FirebaseAuth mAuth;
    public static Context mContext;

    private static CurrentPositionViewModel mViewModel;

    private static ListenerRegistration mListenerAlert;
    public static int new_coronavirus_infection_flag = 0;
    public static Location currentLocation;
    public static Collection<WeightedLatLng> mAlertAreas = new ArrayList<WeightedLatLng>();
    public static Date refreshAlarmAreasTime = null;

    public static void init(final Context context) {
        mFirebaseFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        mContext = context;

        Application app = (Application)mContext.getApplicationContext();
        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();
        mViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(CurrentPositionViewModel.class);

        addAlertListener();
    }

    private static void addAlertListener() {
        mListenerAlert = mFirebaseFirestore.collection("corona-infos")
                .whereEqualTo(FieldPath.documentId(),"update-info")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            return;
                        }
                        try {
                            if(currentLocation != null) {
                                refreshAlarmAreasTime = null;
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
                        } catch (Throwable t) {
                            Log.i(TAG, t.getMessage(), t);
                            if (mContext != null) {
                                Toast.makeText(mContext, t.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                });
    }

    public static void registNewCoronavirusInfo(FirebaseUser currentUser, String locCode) {
        Date date1 = Calendar.getInstance().getTime();
        Timestamp timestamp = new Timestamp(date1);

        registNewCoronavirusInfo(currentUser, locCode, timestamp);
    }
    public static void registNewCoronavirusInfo(FirebaseUser currentUser, String locCode, Timestamp timestamp) {
        WriteBatch batch = mFirebaseFirestore.batch();

        try {
            registNewCoronavirusInfo(batch, currentUser, locCode, timestamp);

            Task<Void> task = batch.commit();
            Tasks.await(task);
        } catch (ExecutionException e) {
            Log.d(TAG, e.getMessage(), e);
        } catch (InterruptedException e) {
            Log.d(TAG, e.getMessage(), e);
        }

        reportNewCoronavirusInfectionComlete();
    }

    public static void registNewCoronavirusInfo(WriteBatch batch, FirebaseUser currentUser, String locCode, Timestamp timestamp) {
        // Create a new user with a first and last name
        Map<String, Object> info = new HashMap<>();
        info.put(currentUser.getEmail().replace(".", "-"), timestamp);

        Map<String, Object> info2 = new HashMap<>();
        info2.put("timestamp", timestamp);

        // Add a new document with a generated ID;
        String locCode1 = locCode.substring(0, Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);
        String locCode2 = locCode.substring(Constants.OPEN_LOCATION_CODE_LENGTH_TO_COMPARE);

        DocumentReference dr1 =
                    mFirebaseFirestore.collection("corona-infos")
                                        .document(locCode1);
        batch.set(dr1, info2, SetOptions.merge());

        DocumentReference dr2 =
                mFirebaseFirestore.collection("corona-infos")
                .document(locCode1)
                .collection("sub-areas")
                .document(locCode2);
        batch.set(dr2, info, SetOptions.merge());

        Log.d(TAG, "registNewCoronavirusInfo: " + dr2.getId() + " => " + info);
    }

    public static void removeNewCoronavirusInfo(final FirebaseUser currentUser, final String locCode) throws ExecutionException, InterruptedException {
        WriteBatch batch = mFirebaseFirestore.batch();

        removeNewCoronavirusInfo(batch, currentUser, locCode);

        batch.commit();
    }

    public static void removeNewCoronavirusInfo(WriteBatch batch, final FirebaseUser currentUser, final String locCode) throws ExecutionException, InterruptedException {
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
        Task<QuerySnapshot> task = mFirebaseFirestore.collection("corona-infos")
                .document(locCode1)
                .collection("sub-areas")
                .whereGreaterThan(field_key, timeStart)
                .get();
        QuerySnapshot snap = Tasks.await(task);
        if (snap != null) {
            List<String> procssed = new ArrayList<String>();
            for (QueryDocumentSnapshot document : snap) {
                String locCode2 = document.getId();

                if (procssed.contains(locCode2)) {
                    continue;
                }
                procssed.add(locCode2);
                Log.d(TAG, "removeNewCoronavirusInfo: " + locCode + " => " + document.getData());

                if (locCode.length() > 6) {
                    if (!locCode2.equals(locCode.substring(6))) {
                        continue;
                    }
                }

                if (!document.contains(field_key)) {
                    continue;
                }
                Map<String, Object> info = new HashMap<>();
                info.put(field_key, FieldValue.delete());

                DocumentReference doc = mFirebaseFirestore.collection("corona-infos")
                        .document(locCode.substring(0, 6))
                        .collection("sub-areas")
                        .document(locCode2);
                batch.set(doc, info, SetOptions.merge());
            }
        }
    }

    public static void reportNewCoronavirusInfection(final FirebaseUser currentUser, int new_coronavirus_infection_flag)
            throws ExecutionException, InterruptedException {
        mListenerAlert.remove();

        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        activityInfo.put("timestamp", Constants.DATE_FORMAT.format(Calendar.getInstance().getTime()));
        activityInfo.put("new_coronavirus_infection_flag", new_coronavirus_infection_flag);

        // Add a new document with a generated ID;
        Task<Void> task = mFirebaseFirestore.collection(currentUser.getEmail())
                .document("status")
                .set(activityInfo, SetOptions.merge());
        Tasks.await(task);

        // 警報基準
        // 日時：　過去７日間
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -1 * SettingInfos.alarm_limit);
        final Date date1 = cal.getTime();
        Timestamp timestamp = new Timestamp(date1);

        if (new_coronavirus_infection_flag == 1) {
            Task<QuerySnapshot> task2 = mFirebaseFirestore.collection(currentUser.getEmail())
                                            .whereGreaterThan("timestamp", timestamp)
                                            .get();
            QuerySnapshot snapshot = Tasks.await(task2);
            WriteBatch batch = mFirebaseFirestore.batch();

            for(DocumentSnapshot doc: snapshot.getDocuments()){
                double latitude = doc.getDouble("latitude");
                double longitude = doc.getDouble("longitude");
                Timestamp timestamp2 = doc.getTimestamp("timestamp");

                OpenLocationCode olc = new OpenLocationCode(latitude, longitude,
                        Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                String locCode = olc.getCode();
                registNewCoronavirusInfo(batch, currentUser, locCode, timestamp2);
            }
            Task<Void> task3 = batch.commit();
            Tasks.await(task3);

            reportNewCoronavirusInfectionComlete();

        } else {
            Task<QuerySnapshot> task2 = mFirebaseFirestore
                                            .collection("corona-infos")
                                            .get();
            QuerySnapshot snapshot = Tasks.await(task2);

            WriteBatch batch = mFirebaseFirestore.batch();
            List<DocumentSnapshot> list = snapshot.getDocuments();
            for(DocumentSnapshot doc: list){
                String docId = doc.getId();
                if (docId.equals("update-info")) {
                    continue;
                }

                //Log.d("firebase-store", "locCode = " + locCode);
                removeNewCoronavirusInfo(batch, currentUser, docId);
            }
            Task<Void> task3 = batch.commit();
            Tasks.await(task3);

            reportNewCoronavirusInfectionComlete();
        }
    }

    private static void reportNewCoronavirusInfectionComlete() {
        Calendar cal = Calendar.getInstance();
        final Date date1 = cal.getTime();
        Timestamp timestamp = new Timestamp(date1);

        addAlertListener();

        Map<String, Object> info2 = new HashMap<>();
        info2.put("timestamp", timestamp);
        DocumentReference doc = mFirebaseFirestore.collection("corona-infos")
                .document("update-info");
        doc.update(info2);
    }
    public static void refreshAlertAreas(LatLng latLng) {
        if (latLng == null) {
            return;
        }

        ArrayList<String> locCodes = new ArrayList<String>();

        OpenLocationCode olc = new OpenLocationCode(latLng.latitude, latLng.longitude,
                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
        String locCode = olc.getCode();
        locCodes.add(locCode);

        refreshAlartAreas(locCodes);
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
        Date now = cal.getTime();

        if (refreshAlarmAreasTime != null && refreshAlarmAreasTime.after(now)) {
            return;
        }
        refreshAlarmAreasTime = Calendar.getInstance().getTime();

        mAlertAreas.clear();
        // コードの構成は、地域コード、都市コード、街区コード、建物コードからなります。
        // 例えば8Q7XPQ3C+J88というコードの場合は、8Q7Xが地域コード（100×100km）、
        // PQが都市コード（5×5km）、3Cが街区コード（250×250m）、
        // +以降のJ88は建物コード（14×14m）を意味しています。
//        if (mGoogleMap != null) {
//            mGoogleMap.setHeatMap(mAlertAreas);
//        }
        mViewModel.selectAlertAreas(mAlertAreas);

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
                                                    for (QueryDocumentSnapshot document : task.getResult()) {
                                                        Log.d(TAG, "refreshAlartAreas: " + document.getId() + " => " + document.getData());

                                                        String locCode2 = document.getId();
                                                        String code = locCode1 + locCode2;

                                                        Map<String, Object> data = document.getData();
                                                        if (data.keySet().size() > 0) {
                                                            addAlertAreas(code, data, mAlertAreas);
                                                        } else {
                                                            document.getReference().delete();
                                                        }
                                                    }

                                                    mViewModel.selectAlertAreas(mAlertAreas);

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

            if (code2.startsWith(code.substring(0, 8)) && !res.contains(code2)) {
                res.add(code2);
            }
        }

        return res;
    }

    public static void removeInflectionInfo(List<String> locCodes) {
        WriteBatch batch = mFirebaseFirestore.batch();
        try {

            for (String locCode : locCodes) {
                removeNewCoronavirusInfo(batch, mAuth.getCurrentUser(), locCode);
            }
            Task<Void> task = batch.commit();
            Tasks.await(task);

            reportNewCoronavirusInfectionComlete();
        } catch (ExecutionException e) {
            Log.d(TAG, e.getMessage(), e);
        } catch (InterruptedException e) {
            Log.d(TAG, e.getMessage(), e);
        }
    }

    public static void maintainace() {
        if (mAuth == null || mAuth.getCurrentUser() == null) {
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1 * SettingInfos.alarm_limit);
        Date date1 = calendar.getTime();

        String docIdMin = Constants.DATE_FORMAT_4_NAME.format(date1);
        final String mail = mAuth.getCurrentUser().getEmail();
        mFirebaseFirestore.collection(mail)
                .whereLessThan(FieldPath.documentId(), docIdMin)
                .get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                        @Override
                        public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                            WriteBatch batch = mFirebaseFirestore.batch();

                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                mFirebaseFirestore.collection(mail).document(doc.getId()).delete();
                            }

                            batch.commit();
                        }
                    });
    }

    public static class InflectionReportTask extends AsyncTask<String, Void, Void>{
        private static final String TAG = "InflectionReportTask";

        @Override
        protected Void doInBackground(String... args) {
            try {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                int flag = Integer.parseInt(args[0]);
                if (flag == 0 || flag == 1) {
                    FireStore.reportNewCoronavirusInfection(auth.getCurrentUser(), flag);
                }
                else if (flag == 5) {
                    List<String> locCodes = new ArrayList<String>();
                    for (int i=1; i<args.length; i++) {
                        locCodes.add(args[i]);
                    }
                    removeInflectionInfo(locCodes);
                }
                else if (flag == 6) {
                    registNewCoronavirusInfo(auth.getCurrentUser(), args[1]);
                }
            } catch (ExecutionException e) {
                Log.i(TAG, e.getMessage(), e);
            } catch (InterruptedException e) {
                Log.i(TAG, e.getMessage(), e);
            }
            return null;
        }
    }
}
