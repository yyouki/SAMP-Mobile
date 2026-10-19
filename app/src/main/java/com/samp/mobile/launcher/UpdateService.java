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
    public ArrayList<String> mUpdateFilesUrl;
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
        Volley.newRequestQueue(getApplicationContext()).add(new StringRequest(
                "https://alynsampmobile.pro/update.json",
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            mUpdateFiles = new ArrayList<>();
                            mUpdateFilesName = new ArrayList<>();
                            mUpdateFilesUrl = new ArrayList<>();
                            mUpdateFilesSize = new ArrayList<>();
                            mUpdateGameDataSize = 0;
                            mUpdateGameDataSizeUpdated = 0;

                            // Alyn's updater exposes separate full-GTA-data and SA-MP-client manifests.
                            // Always choose the full manifest; never fall back to Lite Data.
                            JSONObject updateConfig = new JSONObject(response);
                            String fullListUrl = updateConfig.optString("full_list_url", "").trim();
                            String sampListUrl = updateConfig.optString("samp_list_url", "").trim();
                            mUpdateVersion = 0;
                            mUpdateGameURL = ""; // This fork must not install a third-party launcher APK.

                            if (fullListUrl.isEmpty() || !getFilesInfo(fullListUrl)) {
                                Log.e("UpdateService", "Alyn full-data manifest could not be loaded");
                                mGameStatus = UpdateActivity.GameStatus.Unknown;
                                setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                                notifyGameStatus();
                                return;
                            }

                            if (!sampListUrl.isEmpty() && !getFilesInfo(sampListUrl)) {
                                Log.e("UpdateService", "Alyn SA-MP manifest could not be loaded");
                                mGameStatus = UpdateActivity.GameStatus.Unknown;
                                setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                                notifyGameStatus();
                                return;
                            }

                            if (mUpdateFiles.isEmpty()) {
                                mGameStatus = UpdateActivity.GameStatus.Updated;
                            } else {
                                mGameStatus = UpdateActivity.GameStatus.UpdateRequired;
                            }

                            Log.i("UpdateService", "Alyn Full Data check: " + mUpdateFiles.size()
                                    + " files / " + mUpdateGameDataSize + " bytes");
                            setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                            notifyGameStatus();
                        } catch (JSONException e) {
                            Log.e("UpdateService", "Could not parse Alyn update/data manifests", e);
                            mGameStatus = UpdateActivity.GameStatus.Unknown;
                            setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                            notifyGameStatus();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("UpdateService", "Could not fetch Alyn update configuration", error);
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
                Log.e("UpdateService", "Alyn game-data manifest download failed: " + response, e);
                Util.responseFilesInt = 2;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "AlynFullGameDataManifest");
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
            Log.e("UpdateService", "Alyn game-data manifest was not received");
            return false;
        }

        JSONObject jsonObject = new JSONObject(Util.responseFiles);
        JSONArray jsonArray = jsonObject.optJSONArray("files");
        if (jsonArray == null) {
            Log.e("UpdateService", "Alyn game-data manifest has no files array");
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
            String normalizedName = name.toLowerCase(Locale.ROOT);
            // Do not overwrite runtime logs or user-editable configuration.
            if (normalizedName.equals("samp_log.txt")
                    || normalizedName.equals("svlog.txt")
                    || normalizedName.equals("gtasatelem.set")
                    || normalizedName.equals("gtasamp10.b")
                    || normalizedName.equals(".htaccess")
                    || normalizedName.equals("gta_sa.set")
                    || normalizedName.equals("settings.ini")) {
                continue;
            }

            String relativePath = fileData.getPath();
            String normalizedPath = relativePath.toLowerCase(Locale.ROOT);
            String normalizedSlashes = relativePath.replace('\\', '/');
            if (relativePath.trim().isEmpty()
                    || relativePath.startsWith("/")
                    || relativePath.indexOf(':') >= 0
                    || normalizedSlashes.startsWith("../")
                    || normalizedSlashes.contains("/../")
                    || normalizedSlashes.endsWith("/..")) {
                Log.e("UpdateService", "Rejected unsafe manifest path: " + relativePath);
                return false;
            }

            String fileUrl = fileData.getUrl();
            if (fileUrl == null || fileUrl.trim().isEmpty()) {
                Log.e("UpdateService", "Alyn manifest item has no download URL: " + relativePath);
                return false;
            }
            try {
                URL parsedUrl = new URL(fileUrl);
                if (!"https".equalsIgnoreCase(parsedUrl.getProtocol())
                        && !"http".equalsIgnoreCase(parsedUrl.getProtocol())) {
                    Log.e("UpdateService", "Rejected unsupported download URL: " + fileUrl);
                    return false;
                }
            } catch (MalformedURLException e) {
                Log.e("UpdateService", "Rejected malformed download URL: " + fileUrl, e);
                return false;
            }

            // Alyn's manifest marks texture variants with gpu=all|dxt|etc|pvr.
            String gpu = jsonFile.optString("gpu", "all").toLowerCase(Locale.ROOT);
            if (!"all".equals(gpu)) {
                if ("dxt".equals(gpu) && mGpuType != 1) continue;
                if ("etc".equals(gpu) && mGpuType != 2) continue;
                if ("pvr".equals(gpu) && mGpuType != 3) continue;
            }

            // Keep compatibility with manifests that encode the GPU profile in the path.
            if (normalizedPath.contains(".dxt.") && mGpuType != 1) continue;
            if (normalizedPath.contains(".etc.") && mGpuType != 2) continue;
            if (normalizedPath.contains(".pvr.") && mGpuType != 3) continue;

            if (mUpdateFiles.contains(relativePath)) {
                continue;
            }

            File file = new File(externalRoot, relativePath);
            if (!file.isFile() || file.length() != fileData.getSize()) {
                mUpdateFiles.add(relativePath);
                mUpdateFilesName.add(name);
                mUpdateFilesUrl.add(fileUrl);
                mUpdateFilesSize.add(fileData.getSize());
                mUpdateGameDataSize += fileData.getSize();
                Log.d("UpdateService", "Alyn Full Data file needs sync: " + relativePath
                        + " (" + fileData.getSize() + " bytes)");
            }
        }

        Log.i("UpdateService", "Alyn manifest check added " + mUpdateFiles.size()
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


    public void startDataUpdating() {
        ArrayList<String> paths = new ArrayList<>(mUpdateFiles);
        ArrayList<String> names = new ArrayList<>(mUpdateFilesName);
        ArrayList<String> urls = new ArrayList<>(mUpdateFilesUrl);
        ArrayList<Long> sizes = new ArrayList<>(mUpdateFilesSize);

        mUpdateFiles.clear();
        mUpdateFilesName.clear();
        mUpdateFilesUrl.clear();
        mUpdateFilesSize.clear();

        File externalRoot = getExternalFilesDir(null);
        if (externalRoot == null || paths.size() != urls.size()
                || paths.size() != names.size() || paths.size() != sizes.size()) {
            Log.e("UpdateService", "Alyn download plan is incomplete or external storage is unavailable");
            mUpdateFiles.addAll(paths);
            notifyDataDownloadFailed();
            return;
        }

        final long[] completedBytes = {0L};
        for (int i = 0; i < paths.size(); i++) {
            final int fileIndex = i;
            final String relativePath = paths.get(i);
            final String fileName = names.get(i);
            final long expectedSize = sizes.get(i);
            File target = new File(externalRoot, relativePath);
            File parent = target.getParentFile();

            if (parent == null || (!parent.exists() && !parent.mkdirs()) || target.isDirectory()) {
                Log.e("UpdateService", "Cannot prepare data file path: " + relativePath);
                mUpdateFiles.add(relativePath);
                continue;
            }
            if (target.exists() && !target.delete()) {
                Log.e("UpdateService", "Cannot replace outdated data file: " + relativePath);
                mUpdateFiles.add(relativePath);
                continue;
            }

            mDownloadingStatus = true;
            final long[] lastProgressAt = {System.currentTimeMillis()};
            Log.d("UpdateService", "Downloading Alyn data file " + relativePath + " from " + urls.get(i));

            PRDownloader.download(urls.get(i), parent.getAbsolutePath(), target.getName())
                    .build()
                    .setOnStartOrResumeListener(null)
                    .setOnPauseListener(null)
                    .setOnCancelListener(null)
                    .setOnProgressListener(new OnProgressListener() {
                        @Override
                        public void onProgress(Progress progress) {
                            mDownloadingStatus = true;
                            if (System.currentTimeMillis() - lastProgressAt[0] > 100) {
                                lastProgressAt[0] = System.currentTimeMillis();
                                Message update = Message.obtain(mInHandler, 4);
                                update.getData().putString("status", UpdateActivity.UpdateStatus.DownloadGameData.name());
                                update.getData().putBoolean("withProgress", true);
                                update.getData().putLong("current", completedBytes[0] + progress.currentBytes);
                                update.getData().putLong("total", mUpdateGameDataSize);
                                update.getData().putString("filename", fileName);
                                update.getData().putLong("totalfiles", paths.size());
                                update.getData().putLong("currentfile", fileIndex + 1);
                                update.replyTo = mMessenger;
                                if (mActivityMessenger != null) {
                                    try {
                                        mActivityMessenger.send(update);
                                    } catch (RemoteException e) {
                                        Log.e("UpdateService", "Could not report Alyn download progress", e);
                                    }
                                }
                            }
                        }
                    })
                    .start(new OnDownloadListener() {
                        @Override
                        public void onDownloadComplete() {
                            mDownloadingStatus = false;
                            completedBytes[0] += expectedSize;
                            Log.d("UpdateService", "Completed Alyn data file " + relativePath);
                        }

                        @Override
                        public void onError(Error error) {
                            mDownloadingStatus = false;
                            mUpdateFiles.add(relativePath);
                            Log.e("UpdateService", "Failed to download Alyn data file " + relativePath);
                        }
                    });

            while (mDownloadingStatus) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    mUpdateFiles.add(relativePath);
                    mDownloadingStatus = false;
                    break;
                }
            }
        }

        mDownloadingStatus = false;
        if (!mUpdateFiles.isEmpty()) {
            // Never launch with missing files; Retry re-fetches the authoritative manifest.
            notifyDataDownloadFailed();
            return;
        }

        Log.i("UpdateService", "Alyn Full Data sync completed");
        Message done = Message.obtain(mInHandler, 1);
        done.getData().putBoolean("status", true);
        done.replyTo = mMessenger;
        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(done);
            } catch (RemoteException e) {
                Log.e("UpdateService", "Could not report Alyn Full Data completion", e);
            }
        }
    }

    private void notifyDataDownloadFailed() {
        mDownloadingStatus = false;
        mGameStatus = UpdateActivity.GameStatus.Unknown;
        Message failed = Message.obtain(mInHandler, 3);
        failed.replyTo = mMessenger;
        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(failed);
            } catch (RemoteException e) {
                Log.e("UpdateService", "Could not report incomplete Alyn Full Data download", e);
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
