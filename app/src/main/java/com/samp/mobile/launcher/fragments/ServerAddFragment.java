package com.samp.mobile.launcher.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.launcher.MainActivity;
import com.samp.mobile.launcher.adapters.FavouriteServerAdapter;
import com.samp.mobile.launcher.adapters.FavouriteServerInformationFragment;
import com.samp.mobile.launcher.adapters.ServerAdapter;
import com.samp.mobile.launcher.data.FavoritesInfo;
import com.samp.mobile.launcher.util.ButtonAnimator;
import com.samp.mobile.launcher.util.SAMPServerInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Obfuscate
public class ServerAddFragment extends Dialog {

    FavouriteServerAdapter mFavouriteServerAdapter;
    Activity activity;
    private final boolean connectAfterAdd;

    public ServerAddFragment(Activity a, FavouriteServerAdapter adapter) {
        this(a, adapter, false);
    }

    public ServerAddFragment(Activity a, FavouriteServerAdapter adapter, boolean connectAfterAdd) {
        super(a);
        activity = a;
        mFavouriteServerAdapter = adapter;
        this.connectAfterAdd = connectAfterAdd;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alertdialog_addserver);

        EditText editText = findViewById(R.id.server_host);
        ImageView mClose = findViewById(R.id.server_close);

        findViewById(R.id.server_add).setOnTouchListener(new ButtonAnimator(getContext(), findViewById(R.id.server_add)));
        findViewById(R.id.server_add).setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Pattern compile = Pattern.compile("^(((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}|localhost|(([0-9]{1,3}\\.){3})[0-9]{1,3}):[0-9]{1,5}$");
                Pattern compile2 = Pattern.compile("^(((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,6}|localhost|(([0-9]{1,3}\\.){3})[0-9]{1,3})$");
                Matcher matcher = compile.matcher(editText.getText().toString());
                Matcher matcher2 = compile2.matcher(editText.getText().toString());
                if (matcher.find() || matcher2.find()) {
                    String endpoint = editText.getText().toString().trim();
                    if (!endpoint.contains(":")) {
                        endpoint = endpoint + ":7777";
                    }
                    String[] endpointParts = endpoint.split(":", 2);
                    String serverAddress = endpointParts[0].trim();
                    final int serverPort;
                    try {
                        serverPort = Integer.parseInt(endpointParts[1].trim());
                    } catch (NumberFormatException e) {
                        Toast.makeText(activity, "Invalid port. Use a port from 1 to 65535.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (serverPort < 1 || serverPort > 65535) {
                        Toast.makeText(activity, "Invalid port. Use a port from 1 to 65535.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    boolean serverExists = FavoritesInfo.IsServerExists(
                            getContext(), 0, 0, serverAddress, serverPort, false);
                    if (serverExists) {
                        if (!connectAfterAdd) {
                            Toast.makeText(getContext(), "This server has been already added!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } else {
                        FavoritesInfo.AddServer(getContext(), 0, 0, serverAddress, serverPort);
                        FavoritesInfo.Save(getContext());
                        ((MainActivity) activity).getFavoriteServerList().add(
                                new SAMPServerInfo(0, 0, "Loading...", serverAddress, serverPort,
                                        0, 0, 0, 0, 0, "—"));
                        ((MainActivity) activity).refreshFavoriteServers();
                    }

                    if (connectAfterAdd) {
                        SAMPServerInfo targetServer = new SAMPServerInfo(
                                0, 0, serverAddress, serverAddress, serverPort,
                                0, 0, 0, 0, 0, "—");
                        if (!FavouriteServerInformationFragment.connectToServer(activity, targetServer)) {
                            return;
                        }
                    }
                    dismiss();
                } else {
                    Toast.makeText(activity, "Invalid IP and port. Use IP:PORT or IP. Correct the value and retry.", Toast.LENGTH_LONG).show();
                    return;
                }

               /* if(((MainActivity)activity).interstitialAd.isReady())
                {
                    ((MainActivity)activity).interstitialAd.showAd();
                }*/
            }
        });

        mClose.setOnTouchListener(new ButtonAnimator(getContext(), mClose));
        mClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
    }
}
