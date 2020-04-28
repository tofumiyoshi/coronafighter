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

import com.fumi.coronafighter.Constants;
import com.fumi.coronafighter.CurrentPositionViewModel;
import com.fumi.coronafighter.SettingInfos;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
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
    public static int infection_flag = 0;
    public static Location currentLocation;
    public static Collection<WeightedLatLng> mInflectionAreas = new ArrayList<WeightedLatLng>();
    public static Date refreshInflectionAreasTime = null;

    public static void init(final Context context) {
        mFirebaseFirestore = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        mContext = context;

        Application app = (Application)mContext.getApplicationContext();
        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();
        mViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(CurrentPositionViewModel.class);
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
    }

    public static void registNewCoronavirusInfo(WriteBatch batch, FirebaseUser currentUser, String locCode, Timestamp timestamp) {
        Map<String, Object> info = new HashMap<>();
        info.put("timestamp", timestamp);

        DocumentReference dr1 =
                    mFirebaseFirestore.collection("corona-infos")
                                        .document(locCode);
        batch.set(dr1, info, SetOptions.merge());

        DocumentReference dr2 =
                mFirebaseFirestore.collection("corona-infos")
                .document(locCode)
                .collection("users")
                .document(currentUser.getUid());
        batch.set(dr2, info, SetOptions.merge());

        Log.d(TAG, "registNewCoronavirusInfo: " + dr2.getId() + " => " + info);
    }

    public static void removeNewCoronavirusInfo(final FirebaseUser currentUser, final String locCode) throws ExecutionException, InterruptedException {
        WriteBatch batch = mFirebaseFirestore.batch();

        removeNewCoronavirusInfo(batch, currentUser, locCode);

        batch.commit();
    }

    public static void removeNewCoronavirusInfo(WriteBatch batch, final FirebaseUser currentUser) throws ExecutionException, InterruptedException {
        // Add a new document with a generated ID;
        Task<QuerySnapshot> task = mFirebaseFirestore
                .collectionGroup("users")
                .get();
        QuerySnapshot snap = Tasks.await(task);
        if (snap != null) {
            List<String> processed = new ArrayList<String>();
            for (QueryDocumentSnapshot document : snap) {
                String docpath = document.getReference().getPath();

                if (processed.contains(docpath)) {
                    continue;
                }
                processed.add(docpath);

                if (!docpath.contains(currentUser.getUid())) {
                    continue;
                }

                batch.delete(document.getReference());
            }
        }
    }

    public static void removeNewCoronavirusInfo(WriteBatch batch, final FirebaseUser currentUser, final String locCode) throws ExecutionException, InterruptedException {
        // Add a new document with a generated ID;
        Task<QuerySnapshot> task = mFirebaseFirestore
                .collectionGroup("users")
                .get();
        QuerySnapshot snap = Tasks.await(task);
        if (snap != null) {
            List<String> processed = new ArrayList<String>();
            for (QueryDocumentSnapshot document : snap) {
                String docpath = document.getReference().getPath();

                if (processed.contains(docpath)) {
                    continue;
                }
                processed.add(docpath);

                if (!docpath.contains(locCode) || !docpath.contains(currentUser.getUid())) {
                    continue;
                }

                Log.d(TAG, "removeNewCoronavirusInfo: " + docpath + " => " + document.getData());
                batch.delete(document.getReference());
            }
        }
    }

    public static void reportNewCoronavirusInfection(final FirebaseUser currentUser, int infection_flag)
            throws ExecutionException, InterruptedException {
        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        activityInfo.put("timestamp", Constants.DATE_FORMAT.format(Calendar.getInstance().getTime()));
        activityInfo.put("infection_flag", infection_flag);
        activityInfo.put("mail", currentUser.getEmail());

        // Add a new document with a generated ID;
        Task<Void> task = mFirebaseFirestore.collection("users")
                .document(currentUser.getUid())
                .collection("infos")
                .document("status")
                .set(activityInfo, SetOptions.merge());
        Tasks.await(task);
    }

    public static void refreshInflectionAreas() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.SECOND, -1 * SettingInfos.refresh_alarm_areas_min_interval_second);
        Date now = cal.getTime();

        if (refreshInflectionAreasTime != null && refreshInflectionAreasTime.after(now)) {
            return;
        }
        refreshInflectionAreasTime = Calendar.getInstance().getTime();

        mInflectionAreas.clear();
        // コードの構成は、地域コード、都市コード、街区コード、建物コードからなります。
        // 例えば8Q7XPQ3C+J88というコードの場合は、8Q7Xが地域コード（100×100km）、
        // PQが都市コード（5×5km）、3Cが街区コード（250×250m）、
        // +以降のJ88は建物コード（14×14m）を意味しています。
        mViewModel.selectAlertAreas(mInflectionAreas);

        // 警報基準
        // 日時：　過去７日間
        // エリア：　PQが都市コード（5×5km）
        mFirebaseFirestore.collection("corona-infos")
                .whereGreaterThan("density", 0)
                .get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                final String locCode = document.getId();

                                int cnt = document.getLong("density").intValue();
                                if (cnt <= 0) {
                                    continue;
                                }
                                OpenLocationCode openLocationCode = new OpenLocationCode(locCode);
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
                                    continue;
                                }
                                WeightedLatLng value = new WeightedLatLng(latlng, intensity);

                                Log.d(TAG, "add to inflected areas: " + locCode + ", cnt:" + cnt);
                                mInflectionAreas.add(value);
                            }

                            mViewModel.selectAlertAreas(mInflectionAreas);
                        }
                        else {
                            Log.d(TAG, "Error getting documents: ", task.getException());
                        }
                    }
                });
    }

    public static List<String> findExistInAlertAreasCode(final LatLng latLng) {
        if  (mInflectionAreas == null || mInflectionAreas.size() == 0) {
            return null;
        }

        List<String> res = new ArrayList<String>();

        OpenLocationCode olc = new OpenLocationCode(latLng.latitude, latLng.longitude,
                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
        String code = olc.getCode();

        SphericalMercatorProjection sProjection = new SphericalMercatorProjection(1.0D);
        for (WeightedLatLng item : mInflectionAreas) {
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
        } catch (ExecutionException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public static void maintainace() {
        if (mAuth == null || mAuth.getCurrentUser() == null) {
            return;
        }
        Log.i(TAG, "maintainace...");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -1 * SettingInfos.alarm_limit);
        Date date1 = calendar.getTime();

        String docIdMin = Constants.DATE_FORMAT_4_NAME.format(date1);
        final String uid = mAuth.getCurrentUser().getUid();
        mFirebaseFirestore.collection("users")
                .document(uid)
                .collection("trace-infos")
                .whereLessThan(FieldPath.documentId(), docIdMin)
                .get().addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                        @Override
                        public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                            WriteBatch batch = mFirebaseFirestore.batch();

                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                mFirebaseFirestore.collection(uid).document(doc.getId()).delete();
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
            } catch (Throwable t) {
                Log.i(TAG, t.getMessage(), t);
            }
            return null;
        }
    }
}
