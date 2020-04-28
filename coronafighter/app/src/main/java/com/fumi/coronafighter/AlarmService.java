package com.fumi.coronafighter;

import android.app.Application;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.maps.android.heatmaps.WeightedLatLng;
import com.google.maps.android.projection.SphericalMercatorProjection;
import com.google.openlocationcode.OpenLocationCode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ExecutionException;

public class AlarmService extends Service {
    private static final String TAG = AlarmService.class.getName();

    private Context context;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mFirebaseFirestore;

    private static ListenerRegistration mListenerAlert;

    private static AsyncTask<Void, Void, ArrayList<AlarmInfo>> mTask;
    public static Date refreshAlarmAreasTime = null;

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
        if (mListenerAlert != null) {
            mListenerAlert.remove();
        }
        mListenerAlert = mFirebaseFirestore.collection("corona-infos")
                .whereEqualTo(FieldPath.documentId(),"info")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        Log.i(TAG, "SnapshotListener fired. [corona-infos/info]");

                        if (e != null) {
                            Log.e(TAG, e.getMessage(), e);
                            return;
                        }

                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.SECOND, -1 * SettingInfos.refresh_alarm_areas_min_interval_second);
                        Date date1 = cal.getTime();
                        if (refreshAlarmAreasTime != null && refreshAlarmAreasTime.after(date1)) {
                            return;
                        }
                        refreshAlarmAreasTime = Calendar.getInstance().getTime();

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

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                return res;
            }

            try {
                Collection<WeightedLatLng> inflectionAreas = FireStore.getInflectionAreas();
                Log.i(TAG, "[inflectionAreas info] cnt: " + inflectionAreas.size());
                if (inflectionAreas.size() == 0) {
                    return res;
                }

                ArrayList<AlarmInfo> traceInfos = FireStore.getTraceInfos(user);
                Log.i(TAG, "[trace info] cnt: " + traceInfos.size());
                if (traceInfos.size() == 0) {
                    return res;
                }

                SphericalMercatorProjection sProjection = new SphericalMercatorProjection(1.0D);
                for (WeightedLatLng weightedLatLng : inflectionAreas) {
                    LatLng latLng = sProjection.toLatLng(weightedLatLng.getPoint());
                    OpenLocationCode olc = new OpenLocationCode(latLng.latitude, latLng.longitude,
                            Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
                    String code = olc.getCode();

                    for (AlarmInfo info: traceInfos) {
                        if (code.equals(info.getLocCode())) {
                            info.setIntensity(info.getCnt() * weightedLatLng.getIntensity());

                            Log.i(TAG, "add to alarm areas: " + info.getLocCode() + "(" + info.getIntensity() + ")");
                            res.add(info);
                            break;
                        }
                    }
                }
            } catch (ExecutionException e) {
                Log.e(TAG, e.getMessage(), e);
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage(), e);
            } finally {
                // delete past data not used
                Calendar cal2 = Calendar.getInstance();
                cal2.add(Calendar.SECOND, -1 * SettingInfos.refresh_alarm_areas_min_interval_second);
                Date time2 = cal2.getTime();
                if (refreshAlarmAreasTime != null && refreshAlarmAreasTime.before(time2)) {
                    FireStore.maintainace();
                }
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
    }
}
