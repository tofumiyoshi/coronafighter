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
import com.google.firebase.auth.FirebaseAuth;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment implements OnMapReadyCallback {
    private static final String TAG = "NotificationsFragment";

    private AlarmAreasViewModel mAreasViewModel;
    private CurrentPositionViewModel mViewModel;

    private List<AlarmInfo> mAlarminfos = new ArrayList<AlarmInfo>();

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

    @Override
    public void onStart() {
        super.onStart();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        Application app = getActivity().getApplication();
        ViewModelProvider.NewInstanceFactory factory = new ViewModelProvider.NewInstanceFactory();

        mAreasViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(AlarmAreasViewModel.class);
        // Use the ViewModel
        mAreasViewModel.getSelected().observe(this, new Observer<ArrayList<AlarmInfo>>() {
            @Override
            public void onChanged(final ArrayList<AlarmInfo> infos) {
                if (mMap == null || infos == null || infos.size() <=0) {
                    Log.i(TAG, "No value in map or infos of AlarmAreasViewModel's on change.");
                    return;
                }

                mAlarminfos.clear();
                mAlarminfos.addAll(infos);
                setAlarmAreas(mAlarminfos);
            }
        });

        mViewModel = new ViewModelProvider((ViewModelStoreOwner) app, factory).get(CurrentPositionViewModel.class);
        // Use the ViewModel
        mViewModel.getSelected().observe(this, new Observer<Location>() {
            @Override
            public void onChanged(final Location location) {
                if (mMap == null || location == null) {
                    Log.i(TAG, "No value in map or location of CurrentPositionViewModel's on change.");
                    return;
                }

                float zoom = mMap.getCameraPosition().zoom;
                if (zoom < SettingInfos.map_min_zoom) {
                    zoom = SettingInfos.map_default_zoom;

                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    CameraUpdate camera = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
                    // カメラの位置に移動
                    mMap.moveCamera(camera);
                }
            }
        });

    }

    public void setAlarmAreas(List<AlarmInfo> alarminfos) {
        if (mMap == null || alarminfos == null || alarminfos.size() == 0) {
            return;
        }

        if (mOverlay != null) {
            mOverlay.remove();
        }

        ArrayList<WeightedLatLng> infos = new ArrayList<WeightedLatLng>();
        for (AlarmInfo info: alarminfos) {
            LatLng latLng = new LatLng(info.getLatitude(), info.getLongitude());
            double intensity = info.getIntensity();

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
