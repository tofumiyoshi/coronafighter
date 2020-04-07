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

    private CurrentPositionViewModel mModel;

    private ViewModelStore mViewModelStore;
    private MainHandler mMainHandler;

    private Collection<WeightedLatLng> mAlertAreas = new ArrayList<WeightedLatLng>();

    private final class MainHandler extends Handler {
        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Location pos = (Location) msg.obj;
            mModel.select(pos);
        }
    }

    @Override
    public void onCreate() {
        /** Called when the Application-class is first created. */
        Log.v(TAG,"--- onCreate() in ---");

        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();
        mModel = new ViewModelProvider(
                CoronaFighterApplication.this, factory).get(CurrentPositionViewModel.class);

        mMainHandler = new MainHandler(getMainLooper());

        TracingIntentService.startActionTracing(getApplicationContext(),
                SettingInfos.tracing_time_interval_second,
                SettingInfos.tracing_min_distance_meter);
    }

    @Override
    public void onTerminate() {
        /** This Method Called when this Application finished. */
        Log.v(TAG,"--- onTerminate() in ---");
    }

    public void setCurrentPosition(Location pos){
        if (pos != null) {
            Message msg = mMainHandler.obtainMessage();
            msg.obj = pos;
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
