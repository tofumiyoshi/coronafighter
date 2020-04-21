package com.fumi.coronafighter.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import com.fumi.coronafighter.R;
import com.google.firebase.auth.FirebaseAuth;

public class HomeFragment extends Fragment {

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        TextView textView = root.findViewById(R.id.text_home);
        textView.setText(getResources().getString(R.string.home_msg));

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth != null && auth.getCurrentUser() != null) {
            TextView textViewMail = root.findViewById(R.id.textViewMail);
            textViewMail.setText(auth.getCurrentUser().getEmail());
        }

        return root;
    }
}
