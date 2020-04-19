package com.fumi.coronafighter;

import android.location.Location;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;

public class AlarmAreasViewModel extends ViewModel {
    private final MutableLiveData<ArrayList<AlarmInfo>> selected = new MutableLiveData<ArrayList<AlarmInfo>>();

    public void select(ArrayList<AlarmInfo> item) {
        selected.setValue(item);
    }

    public LiveData<ArrayList<AlarmInfo>> getSelected() {
        return selected;
    }
}
