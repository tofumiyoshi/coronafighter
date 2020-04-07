package com.fumi.coronafighter;

import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.maps.model.LatLng;

public class CurrentPositionViewModel extends ViewModel {
    private final MutableLiveData<Location> selected = new MutableLiveData<Location>();

    public void select(Location item) {
        selected.setValue(item);
        //selected.postValue(item);
    }

    public LiveData<Location> getSelected() {
        return selected;
    }
}
