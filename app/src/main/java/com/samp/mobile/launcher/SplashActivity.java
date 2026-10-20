package com.samp.mobile.launcher;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.net.Uri;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.downloader.Progress;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.joom.paranoid.Obfuscate;
import com.samp.mobile.R;
import com.samp.mobile.launcher.config.Config;
import com.samp.mobile.launcher.util.SharedPreferenceCore;
import com.samp.mobile.launcher.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
@Obfuscate
public class SplashActivity extends AppCompatActivity {

    private static final int REQUEST_GAME_DATA_FOLDER = 5208;
    private final String[] permissions = {"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.RECORD_AUDIO"};
    private ArrayList<String> pendingRequiredPaths = new ArrayList<>();
    private long[] pendingRequiredSizes = new long[0];

    public int mGpuType;

    SharedPreferences prefs = null;

    AlertDialog.Builder builder;

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mService = new Messenger(iBinder);
            checkUpdate();
        }

        public void onServiceDisconnected(ComponentName componentName) {
            mService = null;
        }
    };


    public IncomingHandler mInHandler;
    public Messenger mMessenger;
    public Messenger mService;

    private boolean mIsBind;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize launcher state before onResume. This fork is signed with its own
        // debug key, so the upstream signature check would reject its package signature.
        Config.currentContext = this;
        prefs = getSharedPreferences("com.samp.mobile", MODE_PRIVATE);
        builder = new AlertDialog.Builder(this);

        if (!Util.isNetworkConnected(Config.currentContext)) {
            builder.setMessage("There is no internet connection.\n" +
                            "Please exit, connect to the internet and reenter the launcher")
                    .setCancelable(false)
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false);

                            finishAndRemoveTask();
                            System.exit(0);
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton("", null);
            AlertDialog alert = builder.create();
            alert.setTitle("Info");
            alert.show();
            return;
        }

        mInHandler = new IncomingHandler();
        mMessenger = new Messenger(mInHandler);

        GLSurfaceView.Renderer mGlRenderer = new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                UpdateActivity.eGPUType egputype;
                String glGetString = gl10.glGetString(GL10.GL_EXTENSIONS);
                String glGetString2 = gl10.glGetString(GL10.GL_EXTENSIONS);
                if (glGetString2.contains("GL_IMG_texture_compression_pvrtc")) {
                    egputype = UpdateActivity.eGPUType.PVR;
                    mGpuType = 3;
                } else if (glGetString2.contains("GL_EXT_texture_compression_dxt1") || glGetString2.contains("GL_EXT_texture_compression_s3tc") || glGetString2.contains("GL_AMD_compressed_ATC_texture")) {
                    egputype = UpdateActivity.eGPUType.DXT;
                    mGpuType = 1;
                } else {
                    egputype = UpdateActivity.eGPUType.ETC;
                    mGpuType = 2;
                }
                Log.e("x1y2z", "GPU name: " + glGetString);
                Log.e("x1y2z", "GPU type: " + egputype.name());

                if (isPermissionsGranted()) {
                    mIsBind = bindService(new Intent(SplashActivity.this, UpdateService.class), mConnection, Context.BIND_AUTO_CREATE);
                } else {
                    ActivityCompat.requestPermissions(SplashActivity.this,
                            permissions,
                            1);
                }
            }

            @Override
            public void onSurfaceChanged(GL10 gl10, int i, int i1) {

            }

            @Override
            public void onDrawFrame(GL10 gl10) {

            }
        };

        ConstraintLayout gpuLayout = findViewById(R.id.gpu);
        GLSurfaceView mGlSurfaceView = new GLSurfaceView(this);
        mGlSurfaceView.setRenderer(mGlRenderer);
        gpuLayout.addView(mGlSurfaceView);
    }

    public class IncomingHandler extends Handler {
        public IncomingHandler() {}

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == 4) {
                UpdateActivity.UpdateStatus valueOf = UpdateActivity.UpdateStatus.valueOf(msg.getData().getString("status", ""));
                if (valueOf == UpdateActivity.UpdateStatus.Undefined) {
                    Message obtain = Message.obtain((Handler) null, 5);
                    obtain.replyTo = mMessenger;
                    try {
                        mService.send(obtain);
                    } catch (RemoteException e5) {
                        e5.printStackTrace();
                    }
                } else if (valueOf == UpdateActivity.UpdateStatus.CheckUpdate) {
                    Message obtain2 = Message.obtain((Handler) null, 4);
                    obtain2.replyTo = mMessenger;
                    try {
                        mService.send(obtain2);
                    } catch (RemoteException e6) {
                        e6.printStackTrace();
                    }
                }
            } else if (msg.what == 5) {
                UpdateActivity.GameStatus valueOf2 = UpdateActivity.GameStatus.valueOf(msg.getData().getString("status", ""));
                Log.i("x1y2z", "gameStatus = " + valueOf2);

                if (valueOf2 == UpdateActivity.GameStatus.UpdateRequired
                        || valueOf2 == UpdateActivity.GameStatus.GameUpdateRequired) {
                    // Game files are not bundled or downloaded; import compatible local files instead.
                    requestLocalGameDataImport(msg.getData());
                } else if (valueOf2 == UpdateActivity.GameStatus.Unknown) {
                    if (!isFinishing()) {
                        new AlertDialog.Builder(SplashActivity.this)
                                .setTitle("Game data check failed")
                                .setMessage("The full GTA game-data manifest could not be verified. Check your connection and retry. The launcher will not continue with incomplete data.")
                                .setCancelable(false)
                                .setPositiveButton("Retry", (dialog, which) -> checkUpdate())
                                .setNegativeButton("Exit", (dialog, which) -> finishAndRemoveTask())
                                .show();
                    }
                } else {
                    startActivity(new Intent(SplashActivity.this, MainActivity.class));
                    finish();
                }

            }
        }
    }


    private void requestLocalGameDataImport(Bundle statusData) {
        ArrayList<String> paths = statusData.getStringArrayList("requiredPaths");
        long[] sizes = statusData.getLongArray("requiredSizes");
        if (paths == null || paths.isEmpty() || sizes == null || sizes.length != paths.size()) {
            new AlertDialog.Builder(this)
                    .setTitle("Game-data manifest unavailable")
                    .setMessage("The required game-data file list could not be validated. Check your connection and retry. The launcher will not continue with incomplete data.")
                    .setCancelable(false)
                    .setPositiveButton("Retry", (dialog, which) -> checkUpdate())
                    .setNegativeButton("Exit", (dialog, which) -> finishAndRemoveTask())
                    .show();
            return;
        }
        pendingRequiredPaths = new ArrayList<>(paths);
        pendingRequiredSizes = sizes.clone();
        new AlertDialog.Builder(this)
                .setTitle("Import local GTA data")
                .setMessage("This app does not bundle or download GTA game files. Choose the extracted game-data folder from a GTA: San Andreas Android 2.10 installation you own. Select the folder that directly contains folders such as Text, data, and models. The full manifest will be checked again before the launcher opens.")
                .setCancelable(false)
                .setPositiveButton("Choose folder", (dialog, which) -> openLocalGameDataFolderPicker())
                .setNegativeButton("Exit", (dialog, which) -> finishAndRemoveTask())
                .show();
    }

    private void openLocalGameDataFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_GAME_DATA_FOLDER);
        } catch (RuntimeException e) {
            Log.e("LocalGameDataImport", "Could not open Android's folder picker", e);
            new AlertDialog.Builder(this)
                    .setTitle("Folder picker unavailable")
                    .setMessage("Android could not open a folder picker. Required local game data is still missing, so the launcher will stay closed.")
                    .setPositiveButton("Retry", (dialog, which) -> openLocalGameDataFolderPicker())
                    .setNegativeButton("Exit", (dialog, which) -> finishAndRemoveTask())
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_GAME_DATA_FOLDER) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            new AlertDialog.Builder(this)
                    .setTitle("No folder selected")
                    .setMessage("A compatible local game-data folder is required before the launcher can continue.")
                    .setPositiveButton("Choose folder", (dialog, which) -> openLocalGameDataFolderPicker())
                    .setNegativeButton("Exit", (dialog, which) -> finishAndRemoveTask())
                    .show();
            return;
        }
        Uri sourceTree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(sourceTree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Log.w("LocalGameDataImport", "Could not persist folder access; trying this import anyway", e);
        }
        importMissingLocalFiles(sourceTree);
    }

    private void importMissingLocalFiles(Uri sourceTree) {
        final ArrayList<String> paths = new ArrayList<>(pendingRequiredPaths);
        final long[] sizes = pendingRequiredSizes.clone();
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Importing local game data");
        progress.setMessage("Copying files from the selected folder. Keep the launcher open.");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.show();
        Thread worker = new Thread(() -> {
            int imported = 0;
            IOException failure = null;
            try {
                imported = com.samp.mobile.launcher.util.LocalGameDataImporter.importMissingFiles(
                        SplashActivity.this, sourceTree, paths, sizes);
            } catch (IOException e) {
                failure = e;
            }
            final int importedCount = imported;
            final IOException importFailure = failure;
            runOnUiThread(() -> {
                if (progress.isShowing()) progress.dismiss();
                if (importFailure != null) {
                    Log.e("LocalGameDataImport", "Could not import local data", importFailure);
                    new AlertDialog.Builder(SplashActivity.this)
                            .setTitle("Import failed")
                            .setMessage(importFailure.getMessage() + "\n\nChoose another folder or exit. Existing app files have not been removed.")
                            .setPositiveButton("Choose another folder", (dialog, which) -> openLocalGameDataFolderPicker())
                            .setNegativeButton("Exit", (dialog, which) -> finishAndRemoveTask())
                            .show();
                    return;
                }
                if (importedCount == 0) {
                    new AlertDialog.Builder(SplashActivity.this)
                            .setTitle("No compatible files found")
                            .setMessage("No missing files matched that folder. Select the extracted folder that directly contains Text, data, and models from GTA: San Andreas Android 2.10.")
                            .setPositiveButton("Choose another folder", (dialog, which) -> openLocalGameDataFolderPicker())
                            .setNegativeButton("Exit", (dialog, which) -> finishAndRemoveTask())
                            .show();
                    return;
                }
                Toast.makeText(SplashActivity.this,
                        "Imported " + importedCount + " file(s). Rechecking the full manifest...",
                        Toast.LENGTH_LONG).show();
                checkUpdate(); // Launch only after the full manifest matches local files.
            });
        }, "LocalGameDataImport");
        worker.start();
    }

    public void checkUpdate() {
        Log.d("x1y2z", "checkUpdate");
        Message obtain = Message.obtain((Handler) null, 0);
        obtain.getData().putInt("gputype", mGpuType);
        obtain.replyTo = mMessenger;
        try {
            mService.send(obtain);
        } catch (RemoteException e5) {
            e5.printStackTrace();
        }
    }

    public boolean isPermissionsGranted()
    {
        int size = permissions.length;

        for (int i = 0; i < size; i++) {
            if (ContextCompat.checkSelfPermission(this, permissions[i])
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 1) {
            return;
        }
        if (grantResults.length <= 0 || grantResults[0] != 0) {
            Toast.makeText(this, "Permissions not granted!", Toast.LENGTH_LONG).show();
        } else {
            mIsBind = bindService(new Intent(this, UpdateService.class), mConnection, Context.BIND_AUTO_CREATE);
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mIsBind) {
            unbindService(mConnection);
            mIsBind = false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (prefs.getBoolean("firstrun", true)) {
            new SharedPreferenceCore().setInt(getApplicationContext(), "FPS_LIMIT", 60);
            new SharedPreferenceCore().setInt(getApplicationContext(), "MESSAGE_COUNT", 6);
            new SharedPreferenceCore().setBoolean(getApplicationContext(), "AIM", false);
            new SharedPreferenceCore().setBoolean(getApplicationContext(), "MODIFIED_DATA", false);
            new SharedPreferenceCore().setBoolean(getApplicationContext(), "AML", false);
            new SharedPreferenceCore().setBoolean(getApplicationContext(), "CLEO", false);
            new SharedPreferenceCore().setBoolean(getApplicationContext(), "MLOADER", false);
            new SharedPreferenceCore().setInt(getApplicationContext(), "VERSION", 0);
            prefs.edit().putBoolean("firstrun", false).commit();
        }
    }
}
