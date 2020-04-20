package com.fumi.coronafighter.ui.notifications;

import android.app.Application;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;

import com.fumi.coronafighter.AlarmAreasViewModel;
import com.fumi.coronafighter.AlarmInfo;
import com.fumi.coronafighter.Constants;
import com.fumi.coronafighter.CurrentPositionViewModel;
import com.fumi.coronafighter.R;
import com.fumi.coronafighter.SettingInfos;
import com.fumi.coronafighter.firebase.FireStore;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;
import com.google.openlocationcode.OpenLocationCode;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class NotificationsFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "NotificationsFragment";
    private AlarmAreasViewModel mAreasViewModel;
    private CurrentPositionViewModel mViewModel;

    private List<AlarmInfo> mAlarminfos;

    public FirebaseAuth mAuth;
    private GoogleMap mMap;
    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_notifications, container, false);

        mAuth = FirebaseAuth.getInstance();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        return root;
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        Application app = getActivity().getApplication();
        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();

        mAreasViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(AlarmAreasViewModel.class);
        // Use the ViewModel
        mAreasViewModel.getSelected().observe(this, new Observer<ArrayList<AlarmInfo>>() {
            @Override
            public void onChanged(final ArrayList<AlarmInfo> infos) {
                if (mMap == null || infos.size() <=0) {
                    return;
                }

                float zoom = mMap.getCameraPosition().zoom;
                if (zoom < SettingInfos.map_min_zoom - 3) {
                    zoom = SettingInfos.map_default_zoom;
                }
                LatLng latLng = new LatLng(infos.get(0).getLatitude(), infos.get(0).getLongitude());
                CameraUpdate camera = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
                // カメラの位置に移動
                mMap.moveCamera(camera);

                setAlarmAreas(infos);
                mAlarminfos = infos;
            }
        });

        mViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(CurrentPositionViewModel.class);
        // Use the ViewModel
        mViewModel.getSelected().observe(this, new Observer<Location>() {
            @Override
            public void onChanged(final Location location) {
                if (mMap == null) {
                    return;
                }
                mMap.setMyLocationEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);

                float zoom = mMap.getCameraPosition().zoom;
                if (zoom < SettingInfos.map_min_zoom - 3) {
                    zoom = SettingInfos.map_default_zoom;
                }

                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                CameraUpdate camera = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
                // カメラの位置に移動
                mMap.moveCamera(camera);
            }
        });

    }

    public void setAlarmAreas(List<AlarmInfo> alarminfos) {
        if (mOverlay != null) {
            mOverlay.remove();
        }
        if (alarminfos == null || alarminfos.size() == 0) {
            return;
        }

        ArrayList<WeightedLatLng> infos = new ArrayList<WeightedLatLng>();
        for (AlarmInfo info: alarminfos) {
            LatLng latLng = new LatLng(info.getLatitude(), info.getLongitude());
            double intensity = 0.0;
            if (info.getCnt() >= SettingInfos.infection_saturation_cnt_max) {
                intensity = 1.0;
            }
            else if (info.getCnt() <= SettingInfos.infection_saturation_cnt_min) {
                intensity = 0.0;
            }
            else {
                intensity = ((double) info.getCnt() - SettingInfos.infection_saturation_cnt_min)/
                        (SettingInfos.infection_saturation_cnt_max - SettingInfos.infection_saturation_cnt_min);
            }

            WeightedLatLng weightedLatLng = new WeightedLatLng(latLng, intensity);

            String str = String.format("%+10.4f, %+10.4f, %+10.4f", weightedLatLng.getPoint().x, weightedLatLng.getPoint().y, weightedLatLng.getIntensity());
            Log.e(TAG, "weightedLatLng = " + str);
            infos.add(weightedLatLng);
        }

        mProvider = new HeatmapTileProvider.Builder()
                .weightedData(infos)
                .build();
        // Add a tile overlay to the map, using the heat map tile provider.
        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
    }
}
