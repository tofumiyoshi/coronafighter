package com.fumi.coronafighter;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.heatmaps.WeightedLatLng;

import java.util.ArrayList;
import java.util.Collection;

public class CurrentPositionViewModel extends ViewModel {
    private final MutableLiveData<LatLng> selected = new MutableLiveData<LatLng>();
    private final MutableLiveData<Collection> alertAreas = new MutableLiveData<Collection>();

    public void select(LatLng item) {
        selected.setValue(item);
        //selected.postValue(item);
    }

    public LiveData<LatLng> getSelected() {
        return selected;
    }

    public void selectAlertAreas(Collection list) {
        alertAreas.setValue(list);
    }

    public LiveData<Collection> getAlertAreas() {
        return alertAreas;
    }
}
