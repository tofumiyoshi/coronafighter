package com.fumi.coronafighter.firebase;

import android.os.AsyncTask;
import android.util.Log;

import com.google.maps.android.heatmaps.WeightedLatLng;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

public class GetInflectionAreasAsyncTask extends AsyncTask<Void, Integer, Collection<WeightedLatLng>> {
    private static final String TAG = "GetInflectionAreasAsyncTask";

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
        if (result == null) {
            return;
        }
        FireStore.mInflectionAreas.clear();
        FireStore.mInflectionAreas.addAll(result);
        Log.d(TAG, "inflected areas data cnt: " + FireStore.mInflectionAreas.size());

        FireStore.mViewModel.selectAlertAreas(FireStore.mInflectionAreas);
    }
}