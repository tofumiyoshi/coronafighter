package com.fumi.coronafighter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.fumi.coronafighter.firebase.FireStore;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.openlocationcode.OpenLocationCode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AlarmService extends Service {
    private static final String TAG = "AlarmService";

    private Context context;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirebaseFirestore;

    private static ListenerRegistration mListenerAlert;

    private static AsyncTask<Void, Void, ArrayList<AlarmInfo>> mTask;

    @Override
    public void onCreate() {
        super.onCreate();

        context = getApplicationContext();

        mAuth = FirebaseAuth.getInstance();
        mFirebaseFirestore = FirebaseFirestore.getInstance();

        mTask = new AlarmTask();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int requestCode = 1;
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
                    .setContentText("アラーム検知")
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setWhen(System.currentTimeMillis())
                    .build();

            // startForeground
            startForeground(2, notification);
        }

        startMoniting();

        return START_NOT_STICKY;
    }

    protected void startMoniting() {
        mListenerAlert = mFirebaseFirestore.collection("corona-infos")
                .whereEqualTo(FieldPath.documentId(),"info")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.e(TAG, e.getMessage(), e);
                            return;
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

                        if (mTask.getStatus() == AsyncTask.Status.PENDING) {
                            mTask.execute();
                        }
                        else if (mTask.getStatus() == AsyncTask.Status.FINISHED) {
                            mTask = new AlarmTask();
                            mTask.execute();
                        }
                    }
                });
    }

    private void stopMoniting(){
        mListenerAlert.remove();

        stopForeground(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        stopMoniting();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class AlarmTask extends AsyncTask<Void, Void, ArrayList<AlarmInfo>>{
        @Override
        protected ArrayList<AlarmInfo> doInBackground(Void... voids) {
            Log.d(TAG, "doInBackground ...");

            final ArrayList<AlarmInfo> res = new ArrayList<AlarmInfo>();
            final ArrayList<AlarmInfo> locList = new ArrayList<AlarmInfo>();

            // 警報基準
            // 日時：　過去７日間
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -1 * SettingInfos.alarm_limit);
            final Date date1 = cal.getTime();
            final Timestamp timestamp = new Timestamp(date1);

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                return res;
            }

            // delete past data not used
            FireStore.maintainace();

            Task<QuerySnapshot> task = mFirebaseFirestore
                    .collection("users")
                    .document(user.getEmail())
                    .collection("trace-infos")
                    .whereGreaterThan("timestamp", timestamp)
                    .get();
            try {
                QuerySnapshot snapshot = Tasks.await(task);
                if (snapshot != null) {
                    for(DocumentSnapshot doc: snapshot.getDocuments()){
                        if (!doc.contains("location")) {
                            continue;
                        }

                        String locCode = doc.getString("locationcode");
                        boolean flag = false;
                        for(int i=0; i<locList.size(); i++) {
                            AlarmInfo info = locList.get(i);
                            if (locCode.equals(info.getLocCode())) {
                                info.setCnt(info.getCnt() + 1);
                                flag = true;
                                break;
                            }
                        }

                        if (!flag) {
                            AlarmInfo info = new AlarmInfo();
                            GeoPoint location = doc.getGeoPoint("location");
                            info.setLatitude(location.getLatitude());
                            info.setLongitude(location.getLongitude());
                            info.setLocCode(locCode);
                            info.setCnt(1);

                            locList.add(info);
                        }
                    }

                    chkLoc4Alaram(locList, res);
                }
            } catch (ExecutionException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage(), e);
            }

            Log.i(TAG, "Task finished.");
            return res;
        }

        @Override
        protected void onPostExecute(ArrayList<AlarmInfo> result) {
            Log.d(TAG, "onPostExecute ...");

            CoronaFighterApplication app = (CoronaFighterApplication)getApplication();
            app.setAlarmAreas(result);
        }

        private void chkLoc4Alaram(final ArrayList<AlarmInfo> locList, final ArrayList<AlarmInfo> res) {
            Task<QuerySnapshot> task = mFirebaseFirestore.collection("corona-infos")
                    .get();
            try {
                QuerySnapshot snapshot = Tasks.await(task);
                if (snapshot != null) {
                    List<DocumentSnapshot> list = snapshot.getDocuments();
                    for(DocumentSnapshot doc: list){
                        String docId = doc.getId();
                        if (docId.equals("info")) {
                            continue;
                        }

                        for (final AlarmInfo info : locList) {
                            if (docId.equals(info.getLocCode())) {
                                if (doc.getLong("density") > info.getCnt()) {
                                    info.setCnt(doc.getLong("density").intValue());
                                }
                                res.add(info);
                                Log.d(TAG, "add to alarm areas:" + info.getLocCode() + ", cnt:" + info.getCnt());
                                break;
                            }
                        }

                        CoronaFighterApplication app = (CoronaFighterApplication)getApplication();
                        app.setAlarmAreas(res);
                    }
                }
            } catch (ExecutionException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }
}
