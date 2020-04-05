package com.fumi.coronafighter;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.maps.model.LatLng;

public class CurrentPositionViewModel extends ViewModel {
    private final MutableLiveData<LatLng> selected = new MutableLiveData<LatLng>();

    public void select(LatLng item) {
        selected.setValue(item);
        //selected.postValue(item);
    }

    public LiveData<LatLng> getSelected() {
        return selected;
    }
}
