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
import android.view.PixelCopy;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
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

    public native void sendDialogResponse(int i, int i2, int i3, byte[] str);

    public static SAMP getInstance() {
        return instance;
    }

    private void showTab()
    {

    }

    private void hideTab()
    {

    }

    private void setTab(int id, String name, int score, int ping)
    {

    }

    private void clearTab()
    {

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
        applyFullscreenMode(new SharedPreferenceCore().getBoolean(
                getApplicationContext(), "FULLSCREEN_MODE", true));

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

    private void applyFullscreenMode(boolean enabled) {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (enabled) {
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    controller.hide(WindowInsets.Type.systemBars());
                } else {
                    controller.show(WindowInsets.Type.systemBars());
                }
            }
            return;
        }

        if (enabled) {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyFullscreenMode(new SharedPreferenceCore().getBoolean(
                    getApplicationContext(), "FULLSCREEN_MODE", true));
        }
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
