/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fumi.coronafighter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.location.LocationResult;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.openlocationcode.OpenLocationCode;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Receiver for handling location updates.
 *
 * For apps targeting API level O
 * {@link android.app.PendingIntent#getBroadcast(Context, int, Intent, int)} should be used when
 * requesting location updates. Due to limits on background services,
 * {@link android.app.PendingIntent#getService(Context, int, Intent, int)} should not be used.
 *
 *  Note: Apps running on "O" devices (regardless of targetSdkVersion) may receive updates
 *  less frequently than the interval specified in the
 *  {@link com.google.android.gms.location.LocationRequest} when the app is no longer in the
 *  foreground.
 */
public class LocationUpdatesBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = LocationUpdatesBroadcastReceiver.class.getName();

    static final String ACTION_PROCESS_UPDATES =
            "com.fumi.coronafighter.locationupdatespendingintent.action" +
                    ".PROCESS_UPDATES";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_PROCESS_UPDATES.equals(action)) {
                LocationResult result = LocationResult.extractResult(intent);
                if (result != null) {
                    //List<Location> locations = result.getLocations();
                    //Utils.setLocationUpdatesResult(context, locations);
                    //Utils.sendNotification(context, Utils.getLocationResultTitle(context, locations));
                    Log.i(TAG, Utils.getLocationUpdatesResult(context));

                    FirebaseAuth auth = FirebaseAuth.getInstance();
                    if (auth.getCurrentUser() == null) {
                        Log.i(TAG, "No Login.");
                    }
                    FirebaseFirestore firebaseFirestore = FirebaseFirestore.getInstance();

                    for (Location location : result.getLocations()) {
                        if (location != null) {
                            traceUserInFireStore(location, auth.getCurrentUser(), firebaseFirestore);
                        }
                    }
                }
            }
        }
    }

    private void traceUserInFireStore(Location location, FirebaseUser currentUser, FirebaseFirestore firebaseFirestore) {
        OpenLocationCode olc = new OpenLocationCode(location.getLatitude(), location.getLongitude(),
                Constants.OPEN_LOCATION_CODE_LENGTH_TO_GENERATE);
        final String locCode = olc.getCode();

        // Create a new user with a first and last name
        Map<String, Object> activityInfo = new HashMap<>();
        Date date1 = Calendar.getInstance().getTime();
        Timestamp timestamp = new Timestamp(date1);

        activityInfo.put("timestamp", timestamp);
        GeoPoint gp = new GeoPoint(location.getLatitude(), location.getLongitude());
        activityInfo.put("location", gp);
        activityInfo.put("locationcode", locCode);

        // Add a new document with a generated ID;
        firebaseFirestore.collection("users")
                .document(currentUser.getUid())
                .collection("trace-infos")
                .document(Constants.DATE_FORMAT_4_NAME.format(timestamp.toDate()))
                .set(activityInfo)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "DocumentSnapshot successfully written!");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing document", e);
                    }
                });
    }
}
