package com.fumi.coronafighter;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

import com.google.android.gms.maps.model.LatLng;

public class CoronaFighterApplication extends Application implements ViewModelStoreOwner {
    private final String TAG = CoronaFighterApplication.class.getSimpleName();

    private CurrentPositionViewModel mModel;

    private ViewModelStore mViewModelStore;
    private LatLng currentPosition;
    private MainHandler mMainHandler;

    private final class MainHandler extends Handler {
        public MainHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            LatLng pos = (LatLng)msg.obj;
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
    }

    @Override
    public void onTerminate() {
        /** This Method Called when this Application finished. */
        Log.v(TAG,"--- onTerminate() in ---");
    }

    public void setCurrentPosition(LatLng pos){
        if (pos != null) {
            currentPosition = pos;

            Message msg = mMainHandler.obtainMessage();
            msg.obj = pos;
            mMainHandler.sendMessage(msg);
        }
    }

    public LatLng getCurrentPosition(){
        return currentPosition;
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
