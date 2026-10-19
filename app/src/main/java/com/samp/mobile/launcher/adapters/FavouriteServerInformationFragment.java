package com.samp.mobile.launcher.adapters;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.game.GTASA;
import com.samp.mobile.game.SAMP;
import com.samp.mobile.launcher.MainActivity;
import com.samp.mobile.launcher.data.FavoritesInfo;
import com.samp.mobile.launcher.util.ButtonAnimator;
import com.samp.mobile.launcher.util.ConfigValidator;
import com.samp.mobile.launcher.util.SAMPServerInfo;
import com.samp.mobile.launcher.util.SharedPreferenceCore;

import org.ini4j.Wini;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

@Obfuscate
public class FavouriteServerInformationFragment extends Dialog {

    FavouriteServerAdapter mFavouriteServerAdapter;
    SAMPServerInfo sampServerInfo;
    int position;
    Activity act;

    public FavouriteServerInformationFragment(Activity a, FavouriteServerAdapter adapter, int position, SAMPServerInfo sampServerInfo1)
    {
        super(a);
        act = a;
        mFavouriteServerAdapter = adapter;
        sampServerInfo = sampServerInfo1;
        this.position = position;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.alertdialog_server);

        File file = new File(act.getExternalFilesDir(null), "favoriteservers.txt");
        File file2 = new File(act.getExternalFilesDir(null), "favoriteservers_temp.txt");

        TextView mHostName = findViewById(R.id.server_hostname);
        TextView mIP = findViewById(R.id.server_ip);
        TextView mPort = findViewById(R.id.server_port);
        TextView mOnlineServer = findViewById(R.id.server_online);
        TextView mMode = findViewById(R.id.server_mode);
        TextView mLanguage = findViewById(R.id.server_language);
        ImageView mClose = findViewById(R.id.server_close);
        EditText mServerPassword = findViewById(R.id.server_password);
        Button mSave = findViewById(R.id.save_favorites);

        mSave.setVisibility(View.VISIBLE);

        if(!sampServerInfo.getHasPassword())
            mServerPassword.setVisibility(View.GONE);
        else
            mServerPassword.setVisibility(View.VISIBLE);

        mHostName.setText(sampServerInfo.getServerName());

        mIP.setText(sampServerInfo.getAddress());
        mPort.setText(String.valueOf(sampServerInfo.getPort()));
        mOnlineServer.setText(sampServerInfo.getCurrentPlayerCount() + "/" + sampServerInfo.getMaxPlayerCount());
        mMode.setText(sampServerInfo.getServerMode());

        mLanguage.setText(sampServerInfo.getLanguage());

        mSave.setOnTouchListener(new ButtonAnimator(getContext(), mSave));
        mSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                //try {
                    /*BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
                    BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file2));
                    while (true) {
                        String readLine = bufferedReader.readLine();
                        if (readLine != null) {
                            if (!(mFavouriteServerAdapter.mServersInfo.get(position).getAddress() + ":" + mFavouriteServerAdapter.mServersInfo.get(position).getPort()).equals(readLine)) {
                                bufferedWriter.write(readLine + System.getProperty("line.separator"));
                            }
                        } else {
                            bufferedWriter.close();
                            bufferedReader.close();
                            file.delete();
                            file2.renameTo(file);
                            dismiss();
                            mFavouriteServerAdapter.refreshServers();
                            return;
                        }
                    }*/
                FavoritesInfo.RemoveServer(getContext(), ((MainActivity)act).getFavoriteServerList().get(position).getAddress(), ((MainActivity)act).getFavoriteServerList().get(position).getPort());
                FavoritesInfo.Save(getContext());
                ((MainActivity)act).getFavoriteServerList().remove(position);
                ((MainActivity)act).refreshFavoriteServers();
                dismiss();
                //} catch (IOException e) {
                //    e.printStackTrace();
               // }
            }
        });

        Button mConnect = findViewById(R.id.server_connect);
        mConnect.setOnTouchListener(new ButtonAnimator(getContext(), mConnect));
        mConnect.setOnClickListener(view -> {
            if (connectToServer(act, sampServerInfo, mServerPassword.getText().toString())) {
                dismiss();
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


    public static boolean connectToServer(Activity activity, SAMPServerInfo serverInfo) {
        return connectToServer(activity, serverInfo, "");
    }

    public static boolean connectToServer(Activity activity, SAMPServerInfo serverInfo, String password) {
        if (activity == null || serverInfo == null) {
            return false;
        }

        File externalRoot = activity.getExternalFilesDir(null);
        if (externalRoot == null) {
            Toast.makeText(activity, "External game-data directory is unavailable.", Toast.LENGTH_LONG).show();
            return false;
        }

        ConfigValidator.validateConfigFiles(activity);
        File settingsFile = new File(externalRoot, "SAMP/settings.ini");
        try {
            Wini wini = new Wini(settingsFile);
            wini.put("client", "host", serverInfo.getAddress());
            wini.put("client", "port", serverInfo.getPort());
            wini.put("client", "password", password == null ? "" : password);
            wini.store();
        } catch (Exception e) {
            Log.e("ServerConnect", "Could not save server connection settings", e);
            Toast.makeText(activity, "Could not save server settings. Reopen the launcher and retry.", Toast.LENGTH_LONG).show();
            return false;
        }

        if (new SharedPreferenceCore().getBoolean(activity, "MLOADER")) {
            File profileFile = new File(
                    Environment.getExternalStorageDirectory(),
                    "Android/media/com.samp.mobile/monetloader/compat/profile.json");
            File profileDirectory = profileFile.getParentFile();
            if (profileDirectory == null || (!profileDirectory.exists() && !profileDirectory.mkdirs())
                    || profileFile.isDirectory()) {
                Toast.makeText(activity, "Could not prepare the MLOADER compatibility profile.", Toast.LENGTH_LONG).show();
                return false;
            }

            String profile = "{\n"
                    + "  \"gtasa_name\": \"libGTASA.so\",\n"
                    + "  \"profile_name\": \"SA-MP 0.3.7\",\n"
                    + "  \"compat_scripts\": [],\n"
                    + "  \"samp_name\": \"libsamp.so\",\n"
                    + "  \"receiveignorerpc_pattern\": \"F0B503AF2DE900????B004460068C16A20468847\",\n"
                    + "  \"cnetgame_ctor_pattern\": \"F0B503AF2DE9000788B00D46????9146????0446002079447A44\",\n"
                    + "  \"rakclientinterface_netgame_offset\": 528,\n"
                    + "  \"use_samp_touch_workaround\": true,\n"
                    + "  \"nveventinsertnewest_offset\": 2606320\n"
                    + "}";

            try (FileWriter writer = new FileWriter(profileFile, false)) {
                writer.write(profile);
            } catch (IOException e) {
                Log.e("ServerConnect", "Could not write MLOADER compatibility profile", e);
                Toast.makeText(activity, "Could not write the MLOADER compatibility profile.", Toast.LENGTH_LONG).show();
                return false;
            }
        }

        File requiredText = new File(externalRoot, "Text/american.dxt");
        File alternateTexture = new File(externalRoot, "Textures/fonts/RussianFont.png");
        if (!requiredText.isFile() && !alternateTexture.isFile()) {
            Toast.makeText(activity,
                    "GTA/SA-MP data is missing or incomplete. Finish the Full Data sync, then connect again.",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        try {
            activity.startActivity(new Intent(activity, SAMP.class));
            activity.finish();
            return true;
        } catch (RuntimeException e) {
            Log.e("ServerConnect", "Could not launch the game client", e);
            Toast.makeText(activity, "Could not start the game client.", Toast.LENGTH_LONG).show();
            return false;
        }
    }

}
