package com.fumi.coronafighter;

import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.heatmaps.WeightedLatLng;

import java.util.Collection;

public class CurrentPositionViewModel extends ViewModel {
    private final MutableLiveData<Location> selected = new MutableLiveData<Location>();
    private final MutableLiveData<Collection<WeightedLatLng>> alertAreas = new MutableLiveData<Collection<WeightedLatLng>>();

    public void select(Location item) {
        selected.setValue(item);
    }

    public LiveData<Location> getSelected() {
        return selected;
    }

    public void selectAlertAreas(Collection<WeightedLatLng> item) {
        alertAreas.setValue(item);
    }

    public LiveData<Collection<WeightedLatLng>> getAlertAreas() {
        return alertAreas;
    }
}
