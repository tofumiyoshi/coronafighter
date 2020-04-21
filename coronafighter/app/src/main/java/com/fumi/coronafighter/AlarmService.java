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
                .whereEqualTo(FieldPath.documentId(),"update-info")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            return;
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
                    .collection(user.getEmail())
                    .whereGreaterThan("timestamp", timestamp)
                    .get();
            try {
                QuerySnapshot snapshot = Tasks.await(task);
                if (snapshot != null) {
                    for(DocumentSnapshot doc: snapshot.getDocuments()){
                        double latitude = doc.getDouble("latitude");
                        double longitude = doc.getDouble("longitude");

                        OpenLocationCode olc = new OpenLocationCode(latitude, longitude,
                                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                        String locCode = olc.getCode();

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
                            OpenLocationCode.CodeArea area = olc.decode();
                            info.setLatitude(area.getCenterLatitude());
                            info.setLongitude(area.getCenterLongitude());
                            info.setLocCode(locCode);
                            info.setCnt(1);

                            locList.add(info);
                        }
                    }

                    chkLoc4Alaram(locList, timestamp, res);
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

        private void chkLoc4Alaram(final ArrayList<AlarmInfo> locList, final Timestamp timestamp, final ArrayList<AlarmInfo> res) {
            Task<QuerySnapshot> task = mFirebaseFirestore.collection("corona-infos")
                    .get();
            try {
                QuerySnapshot snapshot = Tasks.await(task);
                if (snapshot != null) {
                    List<DocumentSnapshot> list = snapshot.getDocuments();
                    for(DocumentSnapshot doc: list){
                        String docId = doc.getId();
                        if (docId.equals("update-info")) {
                            continue;
                        }

                        final String locCode1 = docId;
                        for (final AlarmInfo info : locList) {
                            if (info.getLocCode().startsWith(locCode1)) {
                                final String locCode2 = info.getLocCode().substring(6);
                                Task<DocumentSnapshot> task2 = mFirebaseFirestore.collection("corona-infos")
                                        .document(locCode1)
                                        .collection("sub-areas")
                                        .document(locCode2)
                                        .get();

                                DocumentSnapshot documentSnapshot = Tasks.await(task2);
                                if (documentSnapshot != null && documentSnapshot.getData() != null) {
                                    Iterator<String> i = documentSnapshot.getData().keySet().iterator();
                                    while(i.hasNext()) {
                                        String key = i.next();
                                        Timestamp ts = documentSnapshot.getTimestamp(key);

                                        if (ts.compareTo(timestamp) > 0) {
                                            res.add(info);

                                            if (res.size() % 100 == 0) {
                                                CoronaFighterApplication app = (CoronaFighterApplication)getApplication();
                                                app.setAlarmAreas(res);
                                            }

                                            Log.d(TAG, "add to alarm areas:" + info.getLocCode());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
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
