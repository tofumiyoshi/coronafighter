package com.fumi.coronafighter.firebase;

import android.os.AsyncTask;
import android.util.Log;

import com.google.maps.android.heatmaps.WeightedLatLng;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

public class GetInflectionAreasAsyncTask extends AsyncTask<Void, Integer, Collection<WeightedLatLng>> {
    private static final String TAG = "GetInflectionAreasAsyncTask";

    public interface Callback {
        public void setInflectionAreas(Collection<WeightedLatLng> result);
    }

    public Callback callback = null;

    public GetInflectionAreasAsyncTask(Callback callback) {
        this.callback = callback;
    }

    @Override
    protected Collection<WeightedLatLng> doInBackground(Void... voids) {
        try {
            return FireStore.getInflectionAreas();
        } catch (ExecutionException e) {
            Log.e(TAG, e.getMessage(), e);
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        return null;
    }

    @Override
    protected void onPostExecute(Collection<WeightedLatLng> result) {
        if (callback != null) {
            callback.setInflectionAreas(result);
        }
    }
}