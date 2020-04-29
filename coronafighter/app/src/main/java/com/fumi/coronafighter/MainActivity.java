package com.fumi.coronafighter;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.fumi.coronafighter.firebase.FireStore;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback,
        NavController.OnDestinationChangedListener {
    private static final String TAG = "MainActivity";

    private FirebaseAuth mAuth;

    private FirebaseFirestore mFirebaseFirestore;
    private ListenerRegistration mListenerStatus;

    private static final int RC_SIGN_IN = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        //toolbar.setLogo(R.mipmap.ic_launcher);
        setSupportActionBar(toolbar);

        initialize();
    }

    private void initialize() {
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            createSignInIntent();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    Constants.LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        navController.addOnDestinationChangedListener(this);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);

        FireStore.init(getApplicationContext());

        Intent intent = new Intent(getApplication(), LocationService.class);
        startService(intent);

        Intent intent2 = new Intent(getApplication(), AlarmService.class);
        startService(intent2);

        mFirebaseFirestore = FirebaseFirestore.getInstance();
        mListenerStatus = mFirebaseFirestore.collection("users")
                .document(mAuth.getCurrentUser().getUid())
                .collection("infos")
                .whereEqualTo(FieldPath.documentId(),"status")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot queryDocumentSnapshots, @Nullable FirebaseFirestoreException e) {
                        if (queryDocumentSnapshots == null) {
                            Log.i(TAG, e.getMessage(), e);
                            return;
                        }
                        for(DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                            FireStore.infection_flag = document.getLong("infection_flag").intValue();

                            invalidateOptionsMenu();
                            break;
                        }
                    }
                });
    }

    public void createSignInIntent() {
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.EmailBuilder().build(),
                new AuthUI.IdpConfig.PhoneBuilder().build(),
                new AuthUI.IdpConfig.GoogleBuilder().build(),
                new AuthUI.IdpConfig.AnonymousBuilder().build());

        // Create and launch sign-in intent
        startActivityForResult(
                AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .setIsSmartLockEnabled(true)
                        .build(),
                RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);
            if (resultCode == RESULT_OK) {
                initialize();
                return;
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Sign in cancelled.", Toast.LENGTH_LONG).show();
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                Toast.makeText(this, "Sign in failed.", Toast.LENGTH_SHORT).show();

                finishAndRemoveTask();
            }
        }
    }

    /**
     * Android Quickstart:
     * https://developers.google.com/sheets/api/quickstart/android
     *
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[]permissions, @NonNull int[] grantResults) {

        if (requestCode != Constants.LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            initialize();
            return;
        }
        else {
            Toast.makeText(this, "Location Permission denied", Toast.LENGTH_SHORT).show();
            finishAndRemoveTask();
        }
    }

    public void signOut() {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        mListenerStatus.remove();

                        //Toast.makeText(getApplicationContext(), "Sign out completed.", Toast.LENGTH_SHORT).show();
                        finishAndRemoveTask();
                    }
                });
    }

    public void delete() {
        AuthUI.getInstance()
                .delete(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(getApplicationContext(), "Auth delete completed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        int idCurrentDestination = navController.getCurrentDestination().getId();
        if (idCurrentDestination == R.id.navigation_dashboard) {
            if (FireStore.infection_flag == 0) {
                MenuItem item = menu.findItem(R.id.infection_report);
                item.setEnabled(true);

                MenuItem item2 = menu.findItem(R.id.infection_report_cancel);
                item2.setEnabled(false);
            }
            else {
                MenuItem item = menu.findItem(R.id.infection_report);
                item.setEnabled(false);

                MenuItem item2 = menu.findItem(R.id.infection_report_cancel);
                item2.setEnabled(true);
            }
        } else if (idCurrentDestination == R.id.navigation_notifications ||
                idCurrentDestination == R.id.navigation_home) {
            MenuItem item = menu.findItem(R.id.infection_report);
            item.setEnabled(false);
            MenuItem item2 = menu.findItem(R.id.infection_report_cancel);
            item2.setEnabled(false);
            MenuItem item3 = menu.findItem(R.id.refresh_inflection_areas);
            item3.setEnabled(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logout:
                Intent intent = new Intent(getApplication(), LocationService.class);
                stopService(intent);

                Intent intent2 = new Intent(getApplication(), AlarmService.class);
                stopService(intent2);

                signOut();
                break;
            case R.id.infection_report:
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.infection_report)
                        .setMessage(R.string.infection_report_confirm_msg_by_tracing)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                FireStore.refreshInflectionAreasTime = null;
                                new FireStore.InflectionReportTask().execute(Integer.toString(1));
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();

                break;
            case R.id.infection_report_cancel:
                FireStore.refreshInflectionAreasTime = null;
                new FireStore.InflectionReportTask().execute(Integer.toString(0));
                break;

            case R.id.refresh_inflection_areas:
                FireStore.refreshInflectionAreasTime = null;
                FireStore.refreshInflectionAreas();
                break;
        }
        return false;
    }

    @Override
    public void onDestinationChanged(@NonNull NavController controller, @NonNull NavDestination destination, @Nullable Bundle arguments) {
        invalidateOptionsMenu();
    }
}
