package com.fumi.coronafighter;

import android.app.Application;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.heatmaps.WeightedLatLng;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CoronaFighterApplication extends Application implements ViewModelStoreOwner {
    private final String TAG = CoronaFighterApplication.class.getSimpleName();

    private final static int MSG_UPD_CURRENT_LOCATION = 1;
    private final static int MSG_UPD_ALARM_AREAS = 2;

    private CurrentPositionViewModel mModel;
    private AlarmAreasViewModel mAlarmAreasViewModel;

    private ViewModelStore mViewModelStore;
    private MainHandler mMainHandler;

    private Collection<WeightedLatLng> mAlertAreas = new ArrayList<WeightedLatLng>();

    private final class MainHandler extends Handler {
        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPD_CURRENT_LOCATION:
                    Location pos = (Location) msg.obj;
                    mModel.select(pos);
                    break;
                case MSG_UPD_ALARM_AREAS:
                    ArrayList<AlarmInfo> infos = (ArrayList<AlarmInfo>) msg.obj;
                    mAlarmAreasViewModel.select(infos);
                    break;
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        /** Called when the Application-class is first created. */
        Log.v(TAG,"--- onCreate() in ---");

        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();
        mModel = new ViewModelProvider(
                CoronaFighterApplication.this, factory).get(CurrentPositionViewModel.class);

        mAlarmAreasViewModel = new ViewModelProvider(
                CoronaFighterApplication.this, factory).get(AlarmAreasViewModel.class);

        mMainHandler = new MainHandler(getMainLooper());
    }

    @Override
    public void onTerminate() {
        /** This Method Called when this Application finished. */
        Log.v(TAG,"--- onTerminate() in ---");
    }

    public void setCurrentPosition(Location pos){
        if (pos != null) {
            Message msg = mMainHandler.obtainMessage();
            msg.what = MSG_UPD_CURRENT_LOCATION;
            msg.obj = pos;
            mMainHandler.sendMessage(msg);
        }
    }

    public void setAlarmAreas(ArrayList<AlarmInfo> infos){
        if (infos != null) {
            Message msg = mMainHandler.obtainMessage();
            msg.what = MSG_UPD_ALARM_AREAS;
            msg.obj = infos;
            mMainHandler.sendMessage(msg);
        }
    }

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        if (mViewModelStore == null) {
            mViewModelStore = new ViewModelStore();
        }

        return mViewModelStore;
    }
}
