# OpenCEF integration on SAMP-Mobile

Working branch: `opencef-integration-landscape`  
Source baseline: `kuzia15/SAMP-Mobile` branch `GTA-2.10`, commit `c67c8bdfbc95840ec2d016c456a1a978bdef8fbf`.

This branch integrates OpenCEF's existing `core`, `android`, and `host` modules as local Gradle modules. The gameplay Activity gets a hidden-by-default `WEB` control which opens a bundled local smoke-test page. The page can send a JSON event to Kotlin and receive a response through OpenCEF's JS bridge. The launcher is forced into landscape and its main page/server rows are re-laid out for a wide server-browser view.

## Changes

- Adds `:core`, `:android`, and `:host` Gradle modules; no C++ game renderer or SA-MP protocol code is intentionally changed.
- Adds `ActivityBrowserOverlay` to `com.opencef.host`.
- Wires the overlay into `com.samp.mobile.game.SAMP` after the native client initialization attempt, with lifecycle cleanup in `onDestroy()`.
- Adds `app/src/main/assets/opencef/alyn_test.html` for an on-device bridge smoke test.
- Routes the app's launch intent through `SplashActivity`, which reaches server-browser `MainActivity` after the existing update check; gameplay is no longer the launcher entry point.
- Keeps splash, launcher, update, and gameplay Activities in landscape.
- Updates the launcher header and server rows to a PC-style wide layout, while preserving existing view IDs referenced by the Java adapter.
- Removes project-specific signing-store paths from the active Gradle configuration. Debug builds use Gradle's default debug signing; no private release key is committed.
- Applies Google Services and Crashlytics Gradle plugins only when the repository owner supplies `app/google-services.json`. The CI build does not invent Firebase project settings.

## Test the overlay on a device

1. Start the client and open gameplay.
2. Tap `WEB` in the upper-right corner. The smoke-test page should show that the native bridge is ready.
3. Tap **Send test event to Kotlin**. The page should display a JSON response.
4. Close the overlay with `×`. Game touch input should work again.
5. Leave gameplay and verify the Activity closes without a WebView lifecycle crash.

This is only a smoke-test page for integration. It is not a final in-game browser UI. Keep loading limited to bundled local pages until a strict origin allowlist and a reduced bridge security boundary are implemented.

## Build and licensing

The workflow compiles unit tests, compiles Android instrumentation-test sources, and attempts to assemble a combined debug APK. Instrumentation tests are compiled but not run on an emulator. The workflow intentionally does not upload the APK as a public artifact because the upstream source tree includes third-party GTA-related binaries/data.

The source README describes the upstream project as educational/research material; a top-level project license granting redistribution was not found in the inspected source branch. Before publishing binaries or distributing a public fork, clarify permission for the client source and for every third-party binary/data asset. Permission to edit this personal fork does not establish distribution rights for those components.

 
## Current landscape launcher refinements
 
- The main navigation uses labelled touch tabs (Servers, Community, Settings), with icons matched to each tab.
- The hosted-server list's manual-add action opens the existing IP/port dialog and saves the server to Favorites instead of presenting a paid-listing notice.
- Server mode, language, and ping are not invented when the query source does not provide them. Favorite-server mode is set from the actual SA-MP query response when available.
- Every launcher/game Activity in the manifest is configured for landscape. This has been validated by source inspection; orientation and touch behavior still require a device test.
