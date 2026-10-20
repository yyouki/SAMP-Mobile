package com.samp.mobile.game;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.io.File;

import com.bytedance.shadowhook.ShadowHook;
import com.joom.paranoid.Obfuscate;
import com.samp.mobile.launcher.util.SharedPreferenceCore;
import com.samp.mobile.launcher.util.SignatureChecker;
import com.wardrumstudios.utils.WarMedia;

@Obfuscate
public class GTASA extends WarMedia {
    // public static GTASA gtasaSelf = null;
    static String vmVersion;
    private boolean once = false;

    private static final String ORIGINAL_GAME_PACKAGE = "com.rockstargames.gtasa";
    private static boolean gameLibrariesLoaded = false;
    private static String gameRuntimeError =
            "Install your own compatible GTA: San Andreas Android 2.10 copy and import its game data.";

    static {
        ShadowHook.init(new ShadowHook.ConfigBuilder()
                .setMode(ShadowHook.Mode.UNIQUE)
                .build());

        vmVersion = System.getProperty("java.vm.version");
        try {
            System.loadLibrary("ImmEmulatorJ");
        } catch (ExceptionInInitializerError | UnsatisfiedLinkError ignored) {
            // Optional compatibility helper; the game runtime check below reports required files.
        }
    }

    /**
     * Loads the game runtime from this APK for developer builds, or from the
     * user's separately installed original GTA:SA 2.10 app for the private
     * test APK. The distributable test APK does not bundle libGTASA.so.
     */
    public static synchronized boolean ensureGameRuntime(Context context) {
        if (gameLibrariesLoaded) {
            return true;
        }
        if (context == null) {
            gameRuntimeError = "Game runtime cannot be checked without an Android context.";
            return false;
        }

        try {
            try {
                // Standard source builds may include the runtime for local development.
                System.loadLibrary("GTASA");
            } catch (UnsatisfiedLinkError notBundled) {
                final PackageManager packageManager = context.getPackageManager();
                final ApplicationInfo originalGame;
                final PackageInfo originalPackage;
                try {
                    originalGame = packageManager.getApplicationInfo(ORIGINAL_GAME_PACKAGE, 0);
                    originalPackage = packageManager.getPackageInfo(ORIGINAL_GAME_PACKAGE, 0);
                } catch (PackageManager.NameNotFoundException missingGame) {
                    gameRuntimeError = "Compatible GTA: San Andreas was not found. Install your own original Android 2.10 copy; this client does not include the game.";
                    return false;
                }

                String versionName = originalPackage.versionName;
                if (versionName == null || !versionName.startsWith("2.10")) {
                    gameRuntimeError = "Detected GTA: San Andreas version "
                            + (versionName == null ? "unknown" : versionName)
                            + ". This SA-MP client requires the original Android 2.10 runtime.";
                    return false;
                }

                if (originalGame.nativeLibraryDir == null || originalGame.nativeLibraryDir.trim().isEmpty()) {
                    gameRuntimeError = "The installed game does not expose a native library directory.";
                    return false;
                }

                File originalLibrary = new File(originalGame.nativeLibraryDir, "libGTASA.so");
                if (!originalLibrary.isFile()) {
                    gameRuntimeError = "The installed game runtime libGTASA.so is unavailable to this app. No copy is bundled; install a compatible original Android 2.10 build and try again.";
                    return false;
                }
                System.load(originalLibrary.getAbsolutePath());
            }

            System.loadLibrary("bass");
            System.loadLibrary("samp");
            gameLibrariesLoaded = true;
            gameRuntimeError = "";
            return true;
        } catch (UnsatisfiedLinkError | SecurityException | IllegalArgumentException error) {
            gameRuntimeError = "Could not load the compatible GTA: San Andreas runtime: "
                    + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            Log.e("GTASA", gameRuntimeError, error);
            return false;
        }
    }

    public static synchronized String getGameRuntimeError() {
        return gameRuntimeError;
    }
    public static void staticEnterSocialClub()
    {
        //  gtasaSelf.EnterSocialClub();
    }

    public static void staticExitSocialClub() {
        //gtasaSelf.ExitSocialClub();
    }

    public void AfterDownloadFunction() {

    }

    public void EnterSocialClub() {

    }

    public void ExitSocialClub() {

    }

    public boolean ServiceAppCommand(String str, String str2)
    {
        return false;
    }

    public int ServiceAppCommandValue(String str, String str2)
    {
        return 0;
    }

    public native void main();

    public void onActivityResult(int i, int i2, Intent intent)
    {
        super.onActivityResult(i, i2, intent);
    }

    public void onConfigurationChanged(Configuration configuration)
    {
        super.onConfigurationChanged(configuration);
    }

    public void onCreate(Bundle bundle)
    {
        if(!once)
        {
            once = true;
        }

        //if(!SignatureChecker.isSignatureValid(this, getPackageName()))
        //{
        //Toast.makeText(this, "Use original launcher! No remake", Toast.LENGTH_LONG).show();
        //return;
        //}

        System.out.println("GTASA onCreate");
        //  gtasaSelf = this;
        //  wantsAccelerometer = true;

        super.onCreate(bundle);

        if (new SharedPreferenceCore().getBoolean(this, "MLOADER")) {
            try {
                System.loadLibrary("monetloader");
            } catch (ExceptionInInitializerError | UnsatisfiedLinkError e) {
                Log.e("AXL", e.getMessage());
            }
        }
    }

    public void onDestroy()
    {
        System.out.println("GTASA onDestroy");
        super.onDestroy();
    }

    public boolean onKeyDown(int i, KeyEvent keyEvent)
    {
        return super.onKeyDown(i, keyEvent);
    }

    public void onPause()
    {
        System.out.println("GTASA onPause");
        super.onPause();
    }

    public void onRestart()
    {
        System.out.println("GTASA onRestart");
        super.onRestart();
    }

    public void onResume()
    {
        System.out.println("GTASA onResume");
        super.onResume();
    }

    public void onStart()
    {
        System.out.println("GTASA onStart");
        super.onStart();
    }

    public void onStop()
    {
        System.out.println("GTASA onStop");
        super.onStop();
    }

    public native void setCurrentScreenSize(int i, int i2);
}