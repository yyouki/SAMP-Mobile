package com.samp.mobile.game;

import android.content.ContentValues;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.joom.paranoid.Obfuscate;
import com.samp.mobile.game.ui.AttachEdit;
import com.samp.mobile.game.ui.CustomKeyboard;
import com.samp.mobile.game.ui.LoadingScreen;
import com.samp.mobile.game.ui.dialog.DialogManager;
import com.samp.mobile.launcher.util.SharedPreferenceCore;
import com.samp.mobile.launcher.util.SignatureChecker;
import com.opencef.host.ActivityBrowserOverlay;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
@Obfuscate
public class SAMP extends GTASA implements CustomKeyboard.InputListener, HeightProvider.HeightListener {
    private static final String TAG = "SAMP";
    private static SAMP instance;

    private CustomKeyboard mKeyboard;
    private DialogManager mDialog;
    private HeightProvider mHeightProvider;

    private AttachEdit mAttachEdit;
    private LoadingScreen mLoadingScreen;
    private volatile ActivityBrowserOverlay openCefOverlay;
    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "samp-screenshot-writer");
        thread.setDaemon(true);
        return thread;
    });
    private boolean screenshotInProgress = false;

    // The native SA-MP bridge calls these scoreboard callbacks while TAB is held.
    // Keep the player data on the UI thread and render it as a lightweight overlay.
    private FrameLayout scoreboardOverlay;
    private LinearLayout scoreboardRows;
    private final Map<Integer, ScoreboardPlayer> scoreboardPlayers = new LinkedHashMap<>();

    private static final class ScoreboardPlayer {
        final String name;
        final int score;
        final int ping;

        ScoreboardPlayer(String name, int score, int ping) {
            this.name = name == null ? "" : name;
            this.score = score;
            this.ping = ping;
        }
    }

    public native void sendDialogResponse(int i, int i2, int i3, byte[] str);

    public static SAMP getInstance() {
        return instance;
    }

    private void showTab() {
        runOnUiThread(() -> {
            ensureScoreboardOverlay();
            if (scoreboardOverlay == null) {
                return;
            }
            renderScoreboard();
            scoreboardOverlay.setVisibility(View.VISIBLE);
        });
    }

    private void hideTab() {
        runOnUiThread(() -> {
            if (scoreboardOverlay != null) {
                scoreboardOverlay.setVisibility(View.GONE);
            }
        });
    }

    private void setTab(int id, String name, int score, int ping) {
        runOnUiThread(() -> {
            scoreboardPlayers.put(id, new ScoreboardPlayer(name, score, ping));
            if (scoreboardRows != null) {
                renderScoreboard();
            }
        });
    }

    private void clearTab() {
        runOnUiThread(() -> {
            scoreboardPlayers.clear();
            if (scoreboardRows != null) {
                scoreboardRows.removeAllViews();
            }
        });
    }

    private void ensureScoreboardOverlay() {
        if (scoreboardOverlay != null) {
            return;
        }

        FrameLayout content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }

        scoreboardOverlay = new FrameLayout(this);
        scoreboardOverlay.setVisibility(View.GONE);
        scoreboardOverlay.setClickable(true);
        scoreboardOverlay.setFocusable(true);
        scoreboardOverlay.setBackgroundColor(0x99000000);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        panel.setBackgroundColor(0xF01B1E22);

        TextView title = new TextView(this);
        title.setText("PLAYERS");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        panel.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout headings = new LinearLayout(this);
        headings.setOrientation(LinearLayout.HORIZONTAL);
        headings.addView(scoreboardCell("Name", 1.0f, true));
        headings.addView(scoreboardCell("Score", 0.22f, true));
        headings.addView(scoreboardCell("Ping", 0.22f, true));
        panel.addView(headings, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(0xFF555B63);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(4);
        dividerParams.bottomMargin = dp(4);
        panel.addView(divider, dividerParams);

        ScrollView scroll = new ScrollView(this);
        scoreboardRows = new LinearLayout(this);
        scoreboardRows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(scoreboardRows, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.82f),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        scoreboardOverlay.addView(panel, panelParams);
        content.addView(scoreboardOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void renderScoreboard() {
        if (scoreboardRows == null) {
            return;
        }
        scoreboardRows.removeAllViews();
        for (Map.Entry<Integer, ScoreboardPlayer> entry : scoreboardPlayers.entrySet()) {
            ScoreboardPlayer player = entry.getValue();
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(5), 0, dp(5));
            row.addView(scoreboardCell(player.name, 1.0f, false));
            row.addView(scoreboardCell(String.valueOf(player.score), 0.22f, false));
            row.addView(scoreboardCell(String.valueOf(player.ping), 0.22f, false));
            scoreboardRows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private TextView scoreboardCell(String text, float weight, boolean heading) {
        TextView cell = new TextView(this);
        cell.setText(text);
        cell.setTextColor(heading ? 0xFFBFC5CC : 0xFFFFFFFF);
        cell.setTextSize(heading ? 12 : 13);
        cell.setSingleLine(true);
        cell.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cell.setGravity(heading ? Gravity.CENTER : Gravity.CENTER_VERTICAL);
        cell.setPadding(dp(4), dp(2), dp(4), dp(2));
        cell.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        return cell;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showLoadingScreen()
    {

    }

    private void hideLoadingScreen()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingScreen.hide();
            }
        });
    }

    public void setPauseState(boolean pause) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pause) {
                    mDialog.hideWithoutReset();
                    mAttachEdit.hideWithoutReset();
                }
                else {
                    if(mDialog.isShow)
                        mDialog.showWithOldContent();
                    if(mAttachEdit.isShow)
                        mAttachEdit.showWithoutReset();
                }
            }
        });
    }

    public void exitGame(){
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false);

        finishAndRemoveTask();
        System.exit(0);
    }

    public void showDialog(int dialogId, int dialogTypeId, byte[] bArr, byte[] bArr2, byte[] bArr3, byte[] bArr4) {
        final String caption = new String(bArr, StandardCharsets.UTF_8);
        final String content = new String(bArr2, StandardCharsets.UTF_8);
        final String leftBtnText = new String(bArr3, StandardCharsets.UTF_8);
        final String rightBtnText = new String(bArr4, StandardCharsets.UTF_8);
        runOnUiThread(() -> { this.mDialog.show(dialogId, dialogTypeId, caption, content, leftBtnText, rightBtnText); });
    }

    private native void onInputEnd(byte[] str);
    @Override
    public void OnInputEnd(String str)
    {
        byte[] toReturn = null;
        try
        {
            toReturn = str.getBytes("windows-1251");
        }
        catch(UnsupportedEncodingException e)
        {

        }

        try {
            onInputEnd(toReturn);
        }
        catch (UnsatisfiedLinkError e5) {
            Log.e(TAG, e5.getMessage());
        }
    }

    private void showKeyboard()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("AXL", "showKeyboard()");
                mKeyboard.ShowInputLayout();
            }
        });
    }

    private void hideKeyboard()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mKeyboard.HideInputLayout();
            }
        });
    }

    private void showEditObject()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAttachEdit.show();
            }
        });
    }

    private void hideEditObject()
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAttachEdit.hide();
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "**** onCreate");
        super.onCreate(savedInstanceState);

        //if(!SignatureChecker.isSignatureValid(this, getPackageName()))
        //{
            //Toast.makeText(this, "Use original launcher! No remake", Toast.LENGTH_LONG).show();
            //return;
        //}

        //mHeightProvider = new HeightProvider(this);

        mKeyboard = new CustomKeyboard(this);

        mDialog = new DialogManager(this);

        mAttachEdit = new AttachEdit(this);

        mLoadingScreen = new LoadingScreen(this);

        instance = this;

        try {
            initializeSAMP();
        } catch (UnsatisfiedLinkError e5) {
            Log.e(TAG, e5.getMessage());
        }

        // Local OpenCEF smoke test; this overlay is hidden until WEB is tapped.
        try {
            openCefOverlay = new ActivityBrowserOverlay(this, "file:///android_asset/opencef/alyn_test.html");
            openCefOverlay.on("overlay_test_event", payload -> {
                Log.i(TAG, "OpenCEF test event: " + payload);
                ActivityBrowserOverlay overlay = openCefOverlay;
                if (overlay != null) {
                    overlay.emit("overlay_test_result", "{\"ok\":true,\"message\":\"Kotlin received the event\"}");
                }
                return kotlin.Unit.INSTANCE;
            });
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to initialize OpenCEF overlay", e);
        }
    }

    private native void initializeSAMP();



    @Override
    public void onStart() {
        Log.i(TAG, "**** onStart");
        super.onStart();
    }

    @Override
    public void onRestart() {
        Log.i(TAG, "**** onRestart");
        super.onRestart();
    }

    @Override
    public void onResume() {
        Log.i(TAG, "**** onResume");
        super.onResume();
        //mHeightProvider.init(view);
    }

    public native void onEventBackPressed();

    /**
     * F8 captures the current game window and saves it without requesting broad
     * storage permission. Android 10+ writes into the Pictures media collection;
     * older supported releases use the app-specific Pictures directory.
     */
    private void captureScreenshot() {
        if (screenshotInProgress) {
            Toast.makeText(this, "Screenshot is already being saved.", Toast.LENGTH_SHORT).show();
            return;
        }

        View decorView = getWindow().getDecorView();
        int width = decorView.getWidth();
        int height = decorView.getHeight();
        if (width <= 0 || height <= 0) {
            Toast.makeText(this, "Screenshot is not ready yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        screenshotInProgress = true;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        PixelCopy.request(getWindow(), bitmap, copyResult -> {
            if (copyResult != PixelCopy.SUCCESS) {
                screenshotInProgress = false;
                bitmap.recycle();
                Toast.makeText(this, "Screenshot failed (PixelCopy " + copyResult + ").",
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (isFinishing() || isDestroyed()) {
                screenshotInProgress = false;
                bitmap.recycle();
                return;
            }

            screenshotExecutor.execute(() -> {
                String message;
                try {
                    message = saveScreenshot(bitmap);
                } catch (IOException | RuntimeException exception) {
                    Log.e(TAG, "Could not save screenshot", exception);
                    message = "Could not save screenshot.";
                } finally {
                    bitmap.recycle();
                }

                final String resultMessage = message;
                runOnUiThread(() -> {
                    screenshotInProgress = false;
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }, new Handler(Looper.getMainLooper()));
    }

    private String saveScreenshot(Bitmap bitmap) throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(new Date());
        String filename = "SAMP-Mobile_" + timestamp + ".png";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/SAMP-Mobile");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);

            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (imageUri == null) {
                throw new IOException("MediaStore did not create a screenshot entry");
            }

            try {
                OutputStream output = resolver.openOutputStream(imageUri);
                if (output == null) {
                    throw new IOException("Unable to open screenshot output stream");
                }
                try (OutputStream stream = output) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw new IOException("PNG encoder could not write screenshot");
                    }
                }

                ContentValues published = new ContentValues();
                published.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(imageUri, published, null, null);
                return "Screenshot saved to Pictures/SAMP-Mobile.";
            } catch (IOException | RuntimeException exception) {
                resolver.delete(imageUri, null, null);
                throw exception;
            }
        }

        File pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (pictures == null) {
            throw new IOException("App-specific Pictures directory is unavailable");
        }
        File directory = new File(pictures, "SAMP-Mobile");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create screenshot directory");
        }
        File target = new File(directory, filename);
        try (FileOutputStream stream = new FileOutputStream(target)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IOException("PNG encoder could not write screenshot");
            }
        } catch (IOException exception) {
            // Remove incomplete screenshots, but do not mask the original error.
            if (target.exists()) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
            }
            throw exception;
        }
        return "Screenshot saved in the app's Pictures/SAMP-Mobile folder.";
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        onEventBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F8 && event.getRepeatCount() == 0) {
            captureScreenshot();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onEventBackPressed();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onPause() {
        Log.i(TAG, "**** onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(TAG, "**** onStop");
        super.onStop();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "**** onDestroy");
        ActivityBrowserOverlay overlay = openCefOverlay;
        openCefOverlay = null;
        if (overlay != null) {
            overlay.dispose();
        }
        screenshotExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onHeightChanged(int orientation, int height) {
        //mKeyboard.onHeightChanged(height);
        //mDialog.onHeightChanged(height);
    }
}
