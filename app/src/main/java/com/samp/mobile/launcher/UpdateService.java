package com.samp.mobile.launcher;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.OnProgressListener;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.downloader.Progress;
import com.joom.paranoid.Obfuscate;
import com.samp.mobile.launcher.data.FilesData;
import com.samp.mobile.launcher.util.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
@Obfuscate
public class UpdateService extends Service {

    Messenger mMessenger;
    Messenger mActivityMessenger;
    IncomingHandler mInHandler;

    public UpdateActivity.GameStatus mGameStatus = UpdateActivity.GameStatus.Unknown;
    public UpdateActivity.UpdateStatus mUpdateStatus = UpdateActivity.UpdateStatus.Undefined;

    public boolean mDownloadingStatus = false;

    public long mUpdateGameDataSize = 0;
    public long mUpdateGameDataSizeUpdated = 0;
    public String mUpdateGameURL = "";
    public int mUpdateVersion;

    public ArrayList<String> mUpdateFiles;
    public ArrayList<String> mUpdateFilesName;
    public ArrayList<Long> mUpdateFilesSize;

    public int mGpuType = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread handlerThread = new HandlerThread("ServiceStartArguments", 10);
        handlerThread.start();
        PRDownloader.initialize(getApplicationContext(), PRDownloaderConfig.newBuilder().setDatabaseEnabled(true).setReadTimeout(30000).setConnectTimeout(30000).build());
        mInHandler = new IncomingHandler(handlerThread.getLooper());
        mMessenger = new Messenger(mInHandler);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    class IncomingHandler extends Handler {
        public IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            mActivityMessenger = msg.replyTo;
            Message obtain = null;
            Messenger messenger = null;
            if (msg.what == 0) {
                mGpuType = msg.getData().getInt("gputype");
                if(mGpuType == 0)
                {
                    Log.e("x1y2z", "GPU not found");
                    return;
                }
                startUpdating();
            } else if (msg.what == 1) {
                startGameUpdateChecking();
            } else if(msg.what == 2) {
                // Legacy clients may still send the old "update launcher APK" message.
                // Re-enter the data-manifest path instead of downloading/installing update.apk.
                mGpuType = msg.getData().getInt("gputype", mGpuType);
                startUpdating();
            } else if (msg.what == 4) {
                obtain = Message.obtain(mInHandler, 4);
                obtain.getData().putString("status", mUpdateStatus.name());
                UpdateService updateService = UpdateService.this;
                obtain.replyTo = updateService.mMessenger;
                messenger = updateService.mActivityMessenger;
                try {
                    messenger.send(obtain);
                } catch (RemoteException e5) {
                    e5.printStackTrace();
                }
            } else if (msg.what == 5) {
                obtain = Message.obtain(mInHandler, 5);
                obtain.getData().putString("status", mGameStatus.name());
                obtain.replyTo = mMessenger;
                try {
                    mActivityMessenger.send(obtain);
                } catch (RemoteException e5) {
                    e5.printStackTrace();
                }

            } else if (msg.what == 7) {
                mGpuType = msg.getData().getInt("gputype");
                if(mGpuType == 0)
                {
                    Log.e("x1y2z", "GPU not found");
                    return;
                }
                startUpdating();
            }

        }
    }

    void startUpdating()
    {
        setUpdateStatus(UpdateActivity.UpdateStatus.CheckUpdate);
        Volley.newRequestQueue(getApplicationContext()).add(new StringRequest("https://samp-mobile.shop/client_config.json", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    mUpdateFiles = new ArrayList<>();
                    mUpdateFilesName = new ArrayList<>();
                    mUpdateFilesSize = new ArrayList<>();

                    JSONObject jSONObject = new JSONObject(response).getJSONObject("client_config");
                    mUpdateVersion = jSONObject.optInt("version_code", 0);
                    mUpdateGameURL = jSONObject.optString("url_launcher", "");

                    String string = jSONObject.optString("url_cache_files", "");

                    mUpdateGameDataSize = 0;
                    mUpdateGameDataSizeUpdated = 0;
                    if (!getFilesInfo(string)) {
                        mGameStatus = UpdateActivity.GameStatus.Unknown;
                        setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                        notifyGameStatus();
                        return;
                    }

                    // Full Data means every required manifest file for this GPU profile.
                    // This fork never triggers the upstream launcher APK installer flow.
                    if (mUpdateFiles.isEmpty()) {
                        mGameStatus = UpdateActivity.GameStatus.Updated;
                    } else {
                        mGameStatus = UpdateActivity.GameStatus.UpdateRequired;
                    }

                    setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);

                } catch (JSONException e) {
                    Log.e("UpdateService", "Could not parse client/data manifest", e);
                    mGameStatus = UpdateActivity.GameStatus.Unknown;
                    setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                    notifyGameStatus();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("UpdateService", "Could not fetch client/data configuration", error);
                mGameStatus = UpdateActivity.GameStatus.Unknown;
                setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                notifyGameStatus();
            }
        }));
    }

    private void notifyGameStatus() {
        if (mActivityMessenger == null) {
            return;
        }
        Message message = Message.obtain(mInHandler, 5);
        message.getData().putString("status", mGameStatus.name());
        message.replyTo = mMessenger;
        try {
            mActivityMessenger.send(message);
        } catch (RemoteException e) {
            Log.e("UpdateService", "Could not report game-data status", e);
        }
    }

    public boolean getFilesInfo(String response) throws JSONException {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }

        // Reset shared response state before every attempt; otherwise a failed retry could
        // accidentally reuse an earlier manifest and skip required Full Data files.
        Util.responseFiles = "";
        Util.responseFilesInt = 0;

        Thread manifestThread = new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(response).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestMethod("GET");
                connection.connect();

                StringBuilder buffer = new StringBuilder();
                try (InputStream stream = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        buffer.append(line).append('\n');
                    }
                }
                Util.responseFiles = buffer.toString();
                Util.responseFilesInt = 1;
            } catch (IOException e) {
                Log.e("UpdateService", "Full game-data manifest download failed", e);
                Util.responseFilesInt = 2;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "FullGameDataManifest");
        manifestThread.start();

        long deadline = System.currentTimeMillis() + 20000;
        while (Util.responseFilesInt == 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        if (Util.responseFilesInt != 1 || Util.responseFiles == null || Util.responseFiles.isEmpty()) {
            Log.e("UpdateService", "Full game-data manifest was not received");
            return false;
        }

        JSONObject jsonObject = new JSONObject(Util.responseFiles);
        JSONArray jsonArray = jsonObject.optJSONArray("files");
        if (jsonArray == null) {
            Log.e("UpdateService", "Game-data manifest has no files array");
            return false;
        }

        File externalRoot = getExternalFilesDir(null);
        if (externalRoot == null) {
            Log.e("UpdateService", "External files directory is unavailable");
            return false;
        }

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonFile = jsonArray.getJSONObject(i);
            FilesData fileData = new FilesData(
                    jsonFile.getString("name"),
                    jsonFile.getLong("size"),
                    jsonFile.getString("path"),
                    jsonFile.getString("url"));

            String name = fileData.getName();
            // Runtime logs and user-editable configuration are not immutable game assets.
            if (name.equals("samp_log.txt")
                    || name.equals("svlog.txt")
                    || name.equals("gtasatelem.set")
                    || name.equals("GTASAMP10.b")
                    || name.equals(".htaccess")
                    || name.equals("gta_sa.set")
                    || name.equals("settings.ini")) {
                continue;
            }

            String relativePath = fileData.getPath();
            String normalizedPath = relativePath.toLowerCase(Locale.ROOT);

            // Do not apply a Lite Data filter. Download every required manifest file,
            // while excluding only mutually exclusive texture encodings for other GPUs.
            if (normalizedPath.contains(".dxt.") && mGpuType != 1) {
                continue;
            }
            if (normalizedPath.contains(".etc.") && mGpuType != 2) {
                continue;
            }
            if (normalizedPath.contains(".pvr.") && mGpuType != 3) {
                continue;
            }

            File file = new File(externalRoot, relativePath);
            if (!file.exists() || file.length() != fileData.getSize()) {
                mUpdateFiles.add(relativePath);
                mUpdateFilesName.add(name);
                mUpdateFilesSize.add(fileData.getSize());
                mUpdateGameDataSize += fileData.getSize();
                Log.d("UpdateService", "Full-data file needs sync: " + relativePath
                        + " (" + fileData.getSize() + " bytes)");
            }
        }
        Log.i("UpdateService", "Full-data sync check found " + mUpdateFiles.size()
                + " files / " + mUpdateGameDataSize + " bytes for GPU profile " + mGpuType);
        return true;
    }

    public void updateGame() {
        if (isGameUpdateExists()) {
            Log.d("UpdateService", "updateGame exists");
            //setUpdateStatus(UpdateActivity.UpdateStatus.DownloadGame);
            downloadGame();
            return;
        }
        Log.d("UpdateService", "updateGame done");
        //setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
        File file = new File(getExternalFilesDir(null) + "/download/update.apk");
        Message obtain = Message.obtain(mInHandler, 2);
        obtain.getData().putBoolean("status", true);
        obtain.getData().putString("apkPath", file.getAbsolutePath());
        obtain.replyTo = mMessenger;
        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(obtain);
            } catch (RemoteException e5) {
                e5.printStackTrace();
            }
        }
        //setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
    }


    public void startGameUpdateChecking()
    {
        if (!mUpdateFiles.isEmpty()) {
            setUpdateStatus(UpdateActivity.UpdateStatus.DownloadGameData);
            startDataUpdating();
            return;
        }

        Log.d("UpdateService", "updateGameData()");
        Message obtain = Message.obtain(this.mInHandler, 1);
        obtain.getData().putBoolean("status", true);
        obtain.replyTo = mMessenger;
        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(obtain);
            } catch (RemoteException e5) {
                e5.printStackTrace();
            }
        }

    }

    public void setUpdateStatus(UpdateActivity.UpdateStatus updateStatus) {
        if (updateStatus.name().length() != 0 && mUpdateStatus != updateStatus) {
            mUpdateStatus = updateStatus;
            Message obtain = Message.obtain(mInHandler, 4);
            obtain.getData().putString("status", mUpdateStatus.name());
            obtain.replyTo = mMessenger;
            Messenger messenger = mActivityMessenger;
            if (messenger != null) {
                try {
                    messenger.send(obtain);
                } catch (RemoteException e5) {
                    e5.printStackTrace();
                }
            }
        }
    }


    public void startDataUpdating()
    {
        ArrayList arrayList = new ArrayList(mUpdateFiles);
        ArrayList arrayList1 = new ArrayList(mUpdateFilesName);
        ArrayList arrayList2 = new ArrayList(mUpdateFilesSize);
        mUpdateFiles.clear();
        mUpdateFilesName.clear();
        mUpdateFilesSize.clear();
        Ref.IntRef intRef = new Ref.IntRef();
        intRef.element = 0;
        Ref.LongRef longRef1 = new Ref.LongRef();
        longRef1.element = 0;
        while(intRef.element<arrayList.size()) {
            mDownloadingStatus = true;

            String string = getExternalFilesDir(null) + "/" + arrayList.get(intRef.element);

            //Log.d("x1y2z", "Update file path: " + string.replace((CharSequence) arrayList1.get(intRef.element), "") + ", Name:" + arrayList1.get(intRef.element));

            File file = new File(getExternalFilesDir(null), string);
            file.getParentFile().mkdirs();
            if (file.exists()) {
                file.delete();
            }

            Ref.LongRef longRef = new Ref.LongRef();
            longRef.element = System.currentTimeMillis();

            Log.d("x1y2z", "startDataUpdating " + mUpdateGameDataSize + " " + mUpdateGameDataSizeUpdated);

            mDownloadingStatus = true;
            PRDownloader.download("https://samp-mobile.shop/files/" + arrayList.get(intRef.element), string.replace(arrayList1.get(intRef.element).toString(), ""), String.valueOf(arrayList1.get(intRef.element))).build().setOnStartOrResumeListener(null).setOnPauseListener(null).setOnCancelListener(null).setOnProgressListener(new OnProgressListener() {
                @Override
                public void onProgress(Progress progress) {
                    mDownloadingStatus = true;
                    if(System.currentTimeMillis() - longRef.element > 100) {
                        longRef.element = System.currentTimeMillis();
                        Message obtain = Message.obtain(mInHandler, 4);
                        obtain.getData().putString("status", UpdateActivity.UpdateStatus.DownloadGameData.name());
                        obtain.getData().putBoolean("withProgress", true);
                        obtain.getData().putLong("current", longRef1.element+progress.currentBytes);
                        obtain.getData().putLong("total", mUpdateGameDataSize);
                        obtain.getData().putString("filename", (String)arrayList1.get(intRef.element));
                        obtain.getData().putLong("totalfiles", arrayList.size());
                        obtain.getData().putLong("currentfile", intRef.element);
                        if (mActivityMessenger != null) {
                            try {
                                mActivityMessenger.send(obtain);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                }
            }).start(new OnDownloadListener() {
                @Override
                public void onDownloadComplete() {
                    mDownloadingStatus = false;
                    longRef1.element+=(long)arrayList2.get(intRef.element);
                    Log.d("x1y2z", "completed");
                }

                @Override
                public void onError(Error error) {
                    mDownloadingStatus = false;
                    mUpdateFiles.add(String.valueOf(arrayList.get(intRef.element)));
                    Log.d("x1y2z", "error downloadgamedata");
                }
            });

            do {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (mDownloadingStatus);


            intRef.element++;

        }

        mDownloadingStatus = false;

        if (!mUpdateFiles.isEmpty()) {
            // Failed files remain in this list. Do not let the launcher continue with a partial
            // asset set; the Retry action creates a fresh service and rechecks the full manifest.
            mGameStatus = UpdateActivity.GameStatus.Unknown;
            Message failed = Message.obtain(mInHandler, 3);
            if (mActivityMessenger != null) {
                try {
                    mActivityMessenger.send(failed);
                } catch (RemoteException e) {
                    Log.e("UpdateService", "Could not report incomplete full-data download", e);
                }
            }
            return;
        }

        Log.d("UpdateService", "Full game-data sync completed");
        Message obtain = Message.obtain(this.mInHandler, 1);
        obtain.getData().putBoolean("status", true);
        obtain.replyTo = mMessenger;
        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(obtain);
            } catch (RemoteException e5) {
                e5.printStackTrace();
            }
        }
    }

    public void downloadGame()
    {
        Log.d("UpdateService", "downloadGame");
        mDownloadingStatus = true;

        File file = new File(getExternalFilesDir(null) + "/download/update.apk");
        if (file.exists()) {
            file.delete();
        }

        Ref.LongRef longRef = new Ref.LongRef();
        longRef.element = System.currentTimeMillis();

        mDownloadingStatus = true;
        PRDownloader.download(mUpdateGameURL, getExternalFilesDir(null) + "/download", "update.apk").build().setOnStartOrResumeListener(null).setOnPauseListener(null).setOnCancelListener(null).setOnProgressListener(new OnProgressListener() {
            @Override
            public void onProgress(Progress progress) {
                mDownloadingStatus = true;
                if(System.currentTimeMillis() - longRef.element > 100) {
                    longRef.element = System.currentTimeMillis();
                    Message obtain = Message.obtain(mInHandler, 4);
                    obtain.getData().putString("status", UpdateActivity.UpdateStatus.DownloadGame.name());
                    obtain.getData().putBoolean("withProgress", true);
                    obtain.getData().putLong("current", progress.currentBytes);
                    obtain.getData().putLong("total", progress.totalBytes);
                    obtain.getData().putString("filename", "update.apk");
                    obtain.getData().putLong("totalfiles", 1);
                    obtain.getData().putLong("currentfile", 1);
                    if (mActivityMessenger != null) {
                        try {
                            mActivityMessenger.send(obtain);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }

            }
        }).start(new OnDownloadListener() {
            @Override
            public void onDownloadComplete() {
                Message obtain = Message.obtain(UpdateService.this.mInHandler, 2);
                obtain.getData().putBoolean("status", true);
                obtain.getData().putString("apkPath", file.getAbsolutePath());
                obtain.replyTo = mMessenger;
                if (mActivityMessenger != null) {
                    try {
                        mActivityMessenger.send(obtain);
                    } catch (RemoteException e5) {
                        e5.printStackTrace();
                    }
                }
                setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                mDownloadingStatus = false;
                Log.d("x1y2z", "completed");
            }

            @Override
            public void onError(Error error) {
                mDownloadingStatus = false;
                downloadGame();
                Log.d("x1y2z", "error downloadgame");
            }
        });

        do {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } while (mDownloadingStatus);

        mDownloadingStatus = false;
    }

    public boolean isGameUpdateExists() {
        PackageInfo packageInfo = null;
        try {
            packageInfo = getPackageManager().getPackageInfo("com.samp.mobile", PackageManager.GET_ACTIVITIES);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        Log.d("x1y2z", "isGameUpdateExists -> currentVersion " + packageInfo.versionCode + " | mUpdateVersion " + this.mUpdateVersion);
        return packageInfo.versionCode == this.mUpdateVersion ? false:true;
    }

    private void sendLoadingScreen(boolean unpacking, String filename, long current, long total) {
        new Thread(new Runnable() {
            public void run() {
                Message obtain = Message.obtain(UpdateService.this.mInHandler, 4);
                obtain.getData().putString("status", UpdateActivity.UpdateStatus.CheckUpdate.name());
                obtain.getData().putBoolean("withProgress", true);
                obtain.getData().putString("filename", filename);
                obtain.getData().putBoolean("unpacking", unpacking);
                obtain.getData().putLong("current", current);
                obtain.getData().putLong("total", total);
                obtain.replyTo = mMessenger;
                if (mActivityMessenger != null) {
                    try {
                        mActivityMessenger.send(obtain);
                    } catch (RemoteException e5) {
                        e5.printStackTrace();
                    }
                }
            }
        }).start();
    }


}
