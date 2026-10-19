package com.samp.mobile.launcher.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.launcher.MainActivity;
import com.samp.mobile.launcher.adapters.FavouriteServerAdapter;

@Obfuscate
public class ServerPagesItemFragment extends Fragment {

    private int pagePosition;

    public static ServerPagesItemFragment newInstance(int page) {
        ServerPagesItemFragment instance = new ServerPagesItemFragment();
        Bundle bundle = new Bundle();
        bundle.putInt("page", page);
        instance.setArguments(bundle);
        return instance;
    }

    public int getPage() {
        return pagePosition;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            pagePosition = getArguments().getInt("page", 0);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_favorite, container, false);
        Activity activity = getActivity();
        if (activity == null) {
            return view;
        }

        MainActivity.hideKeyboard(activity);
        FavouriteServerAdapter adapter = new FavouriteServerAdapter(activity);
        RecyclerView recyclerView = view.findViewById(R.id.server_recycler);
        if (recyclerView.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setAdapter(adapter);

        View addServerButton = view.findViewById(R.id.buttonServer);
        if (addServerButton != null) {
            addServerButton.setOnClickListener(ignored -> {
                ServerAddFragment addServerDialog = new ServerAddFragment(activity, adapter);
                addServerDialog.show();
            });
        }
        return view;
    }
}
