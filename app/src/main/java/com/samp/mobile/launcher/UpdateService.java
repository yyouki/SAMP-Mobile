package com.samp.mobile.launcher;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import com.joom.paranoid.Obfuscate;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/** Checks local game data using metadata only; game assets are never downloaded or bundled. */
@Obfuscate
public class UpdateService extends Service {
    private static final String UPDATE_CONFIG_URL = "https://alynsampmobile.pro/update.json";
    Messenger mMessenger;
    Messenger mActivityMessenger;
    IncomingHandler mInHandler;
    public UpdateActivity.GameStatus mGameStatus = UpdateActivity.GameStatus.Unknown;
    public UpdateActivity.UpdateStatus mUpdateStatus = UpdateActivity.UpdateStatus.Undefined;
    public long mUpdateGameDataSize = 0L;
    public int mGpuType = 0;
    private final ArrayList<String> mUpdateFiles = new ArrayList<>();
    private final ArrayList<Long> mUpdateFilesSize = new ArrayList<>();
    private boolean mManifestCheckRunning;

    @Override public void onCreate() {
        super.onCreate();
        HandlerThread thread = new HandlerThread("LocalGameDataManifestCheck", 10);
        thread.start();
        mInHandler = new IncomingHandler(thread.getLooper());
        mMessenger = new Messenger(mInHandler);
    }
    @Override public IBinder onBind(Intent intent) { return mMessenger.getBinder(); }

    private final class IncomingHandler extends Handler {
        IncomingHandler(Looper looper) { super(looper); }
        @Override public void handleMessage(Message msg) {
            if (msg.replyTo != null) mActivityMessenger = msg.replyTo;
            switch (msg.what) {
                case 0:
                case 2:
                case 7:
                    mGpuType = msg.getData().getInt("gputype", mGpuType);
                    startUpdating();
                    break;
                case 1: startGameUpdateChecking(); break;
                case 4: sendUpdateStatus(); break;
                case 5: notifyGameStatus(); break;
                default: Log.w("UpdateService", "Ignoring unsupported message " + msg.what); break;
            }
        }
    }

    private void startUpdating() {
        synchronized (this) {
            if (mManifestCheckRunning) return;
            mManifestCheckRunning = true;
        }
        Thread worker = new Thread(() -> {
            UpdateActivity.GameStatus result = UpdateActivity.GameStatus.Unknown;
            try {
                clearManifestState();
                JSONObject config = new JSONObject(readMetadata(UPDATE_CONFIG_URL));
                String fullUrl = config.optString("full_list_url", "").trim();
                String sampUrl = config.optString("samp_list_url", "").trim();
                if (fullUrl.isEmpty() || !getFilesInfo(fullUrl)) {
                    throw new IOException("Required full-data metadata manifest is unavailable.");
                }
                if (!sampUrl.isEmpty() && !getFilesInfo(sampUrl)) {
                    throw new IOException("Required SA-MP metadata manifest is unavailable.");
                }
                result = mUpdateFiles.isEmpty() ? UpdateActivity.GameStatus.Updated : UpdateActivity.GameStatus.UpdateRequired;
                Log.i("UpdateService", "Local data validation found " + mUpdateFiles.size()
                        + " missing files (" + mUpdateGameDataSize + " bytes), GPU profile " + mGpuType);
            } catch (Exception e) {
                Log.e("UpdateService", "Could not validate local game data against metadata manifests", e);
            } finally {
                mGameStatus = result;
                mUpdateStatus = UpdateActivity.UpdateStatus.Undefined;
                synchronized (UpdateService.this) { mManifestCheckRunning = false; }
                notifyGameStatus();
            }
        }, "LocalGameDataManifestCheck");
        worker.start();
    }

    private void clearManifestState() {
        mUpdateFiles.clear();
        mUpdateFilesSize.clear();
        mUpdateGameDataSize = 0L;
    }

    /** Fetch manifest metadata via HTTPS; asset URL fields in JSON are deliberately ignored. */
    private String readMetadata(String address) throws IOException {
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("Manifest metadata must use HTTPS.");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        try {
            connection.connect();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("Metadata request returned HTTP " + code);
            StringBuilder body = new StringBuilder();
            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line).append('\n');
            }
            return body.toString();
        } finally { connection.disconnect(); }
    }

    /** Checks paths, sizes and GPU tags; it never opens the URL for an individual asset. */
    public boolean getFilesInfo(String manifestUrl) throws JSONException {
        final String body;
        try { body = readMetadata(manifestUrl); }
        catch (IOException e) {
            Log.e("UpdateService", "Could not read metadata manifest: " + manifestUrl, e);
            return false;
        }
        JSONArray entries = new JSONObject(body).optJSONArray("files");
        if (entries == null) {
            Log.e("UpdateService", "Manifest does not contain a files array");
            return false;
        }
        File root = getExternalFilesDir(null);
        if (root == null) {
            Log.e("UpdateService", "Local game-data directory is unavailable");
            return false;
        }
        final String rootPrefix;
        try { rootPrefix = root.getCanonicalPath() + File.separator; }
        catch (IOException e) {
            Log.e("UpdateService", "Could not resolve local data directory", e);
            return false;
        }

        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            String name = entry.optString("name", "").trim();
            String path = entry.optString("path", "").replace('\\', '/');
            long size = entry.optLong("size", -1L);
            if (name.isEmpty() || size < 0L || !isSafeRelativePath(path)) {
                Log.e("UpdateService", "Rejected invalid manifest item at index " + i);
                return false;
            }
            String baseName = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
            if (isProtectedUserFile(name.toLowerCase(Locale.ROOT)) || isProtectedUserFile(baseName)) continue;

            String normalizedPath = path.toLowerCase(Locale.ROOT);
            String gpu = entry.optString("gpu", "all").toLowerCase(Locale.ROOT);
            if (!"all".equals(gpu)) {
                if ("dxt".equals(gpu) && mGpuType != 1) continue;
                if ("etc".equals(gpu) && mGpuType != 2) continue;
                if ("pvr".equals(gpu) && mGpuType != 3) continue;
                if (!"dxt".equals(gpu) && !"etc".equals(gpu) && !"pvr".equals(gpu)) {
                    Log.e("UpdateService", "Rejected unsupported GPU tag " + gpu);
                    return false;
                }
            }
            if (normalizedPath.contains(".dxt.") && mGpuType != 1) continue;
            if (normalizedPath.contains(".etc.") && mGpuType != 2) continue;
            if (normalizedPath.contains(".pvr.") && mGpuType != 3) continue;
            if (mUpdateFiles.contains(path)) continue;

            File candidate = new File(root, path);
            try {
                if (!candidate.getCanonicalPath().startsWith(rootPrefix)) {
                    Log.e("UpdateService", "Rejected file path outside local data directory: " + path);
                    return false;
                }
            } catch (IOException e) {
                Log.e("UpdateService", "Could not resolve manifest path: " + path, e);
                return false;
            }
            if (!candidate.isFile() || candidate.length() != size) {
                mUpdateFiles.add(path);
                mUpdateFilesSize.add(size);
                mUpdateGameDataSize += size;
            }
        }
        return true;
    }

    private boolean isSafeRelativePath(String path) {
        if (path == null || path.trim().isEmpty() || path.startsWith("/") || path.indexOf(':') >= 0) return false;
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) return false;
        }
        return true;
    }

    private boolean isProtectedUserFile(String name) {
        return "samp_log.txt".equals(name) || "svlog.txt".equals(name) || "gtasatelem.set".equals(name)
                || "gtasamp10.b".equals(name) || ".htaccess".equals(name)
                || "gta_sa.set".equals(name) || "settings.ini".equals(name);
    }

    private void sendUpdateStatus() {
        Messenger target = mActivityMessenger;
        if (target == null) return;
        Message message = Message.obtain(null, 4);
        message.getData().putString("status", mUpdateStatus.name());
        message.replyTo = mMessenger;
        sendToActivity(message, "Could not report metadata status");
    }

    private void notifyGameStatus() {
        Messenger target = mActivityMessenger;
        if (target == null) return;
        Message message = Message.obtain(null, 5);
        message.getData().putString("status", mGameStatus.name());
        message.getData().putStringArrayList("requiredPaths", new ArrayList<>(mUpdateFiles));
        long[] sizes = new long[mUpdateFilesSize.size()];
        for (int i = 0; i < mUpdateFilesSize.size(); i++) sizes[i] = mUpdateFilesSize.get(i);
        message.getData().putLongArray("requiredSizes", sizes);
        message.replyTo = mMessenger;
        sendToActivity(message, "Could not report local data status");
    }

    private void startGameUpdateChecking() {
        if (!mUpdateFiles.isEmpty()) {
            // Legacy callers remain fail-closed; the automatic data download path is removed.
            Message failure = Message.obtain(null, 3);
            failure.replyTo = mMessenger;
            sendToActivity(failure, "Could not report missing local data");
            return;
        }
        Message done = Message.obtain(null, 1);
        done.getData().putBoolean("status", true);
        done.replyTo = mMessenger;
        sendToActivity(done, "Could not report complete local data");
    }

    private void sendToActivity(Message message, String description) {
        Messenger target = mActivityMessenger;
        if (target == null) return;
        try { target.send(message); }
        catch (RemoteException e) { Log.e("UpdateService", description, e); }
    }
}
