# SA-MP Mobile Fork — Client Feature Roadmap

This is the implementation backlog for the private `opencef-integration-landscape` branch. Keep `main` unchanged. Do not publish APKs or game assets publicly; use the private build repository and short-lived Actions artifacts for device testing.

## Phase 0 — Stability and Android compatibility

- Preserve the working first-launch preferences fix in `SplashActivity`.
- [x] Upgrade the Android build toolchain to compile/target Android 16 (API 36). Device regression checks for edge-to-edge, Back, and landscape behavior remain pending.
- Review predictive Back, edge-to-edge insets, landscape behavior, permissions, and runtime compatibility on Android 16.
- Keep arm32/arm64 ABI declarations truthful; a Gradle ABI filter is not proof that all native gameplay/mod libraries exist for both ABIs.
- Exit criteria: CI tests and combined debug APK build pass; device smoke test covers first launch, background/resume, Back, dialogs, and gameplay entry.

## Phase 1 — Launcher and PC-style menus

- [x] Remove the Hosted-server feed/tab and its network request; retain Favorites and manual IP/port addition.
- Redesign dialogs and the scoreboard/tab display to resemble desktop SA-MP conventions while remaining readable in landscape on a phone.
- Change game settings presentation toward the GTA: San Andreas settings organization without copying external artwork/assets unless rights are clear.
- Add a client-side command console/window for supported client commands, with safe parsing and a documented list of commands.
- Add an F8 screenshot action in a PC-like keyboard position/flow, with Android 16-compatible storage and media handling.
- Exit criteria: Favorites still connect, server add/remove works, dialogs and scoreboard render correctly, and screenshot save/share is verified on the device.

## Phase 2 — HUD, keyboard, mouse, and controls

- Map physical keyboard keys and mouse buttons to supported game actions; define consistent Tab, Escape, Enter, arrows, and F-keys behavior.
- Test external USB/Bluetooth keyboard and mouse. Support common key-mapping adapters such as Mobilador where input reaches Android as standard key/motion events; do not promise compatibility with every mapper.
- Provide explicit HUD controls for defensive/block action and passenger entry if the underlying client/game callback supports them.
- Add toggles for fullscreen, FPS display, automatic reconnect, push-to-talk, gyroscope/tilt input, and configurable control visibility.
- Ensure all toggles persist, have a clear state, and can be reversed in Settings.

## Phase 3 — Chat, voice, and quality-of-life

- Chat log controls: font size, visible line count, horizontal/vertical offset, and timestamps.
- Voice controls: microphone capture volume, incoming voice volume, and push-to-talk enable/disable, subject to an available and compatible voice implementation.
- Add a device-level permissions/error path for microphone access and test mute/unmute and Activity lifecycle behavior.

## Phase 4 — SA-MP/open.mp gameplay integration

- Verify and implement 0.3DL client compatibility, including download handling and server compatibility.
- Promote OpenCEF from its bundled local smoke-test page to a controlled client feature only after defining origin allowlists, JavaScript bridge limits, navigation rules, and lifecycle/error handling.
- Support PC-style toy/object attachment editing only after identifying the native client callbacks, data formats, and server-side protocol requirements.
- Implement JPatch, modify mode, and GPS controls only where the fork has a real backend for those operations; toggles must not be placeholders.

## Phase 5 — Mod-loader compatibility and data profiles

- Audit AML, MoonLoader, and CLEO support separately for 32-bit `armeabi-v7a` and 64-bit `arm64-v8a`.
- Verify each loader's source/version, license, JNI/native ABI, Android 16 behavior, injection/initialization order, and interaction with the client before enabling it.
- Add explicit Full Data / Lite Data profiles with checks for required files, free space, rollback, and a clear data-version indicator.
- Add optional weapon-icon display settings for in-game nametags and kill-feed only if the server/client protocol and native UI render hooks expose the required weapon IDs reliably.
- Exit criteria: each loader/profile has an isolated smoke test, documented unsupported combinations, and no claim of cross-ABI support without build evidence.

## Phase 6 — Release gates

- Run JVM/unit tests, Android test-source compilation, native build, APK inspection, and install/launch smoke tests.
- Test on the user's Android 16 phone; CI cannot prove physical-device behavior.
- Keep APK artifacts private and short-lived. Do not change `main`, publish releases, or redistribute upstream GTA-related binaries/data without resolving licensing and permission questions.

## Current status

The private combined APK builds, the first-launch `SharedPreferences` crash was fixed, the Hosted feed/tab was removed, and the branch now targets Android 16/API 36. Runtime regression testing on the user's phone is still required. The OpenCEF page remains a local bridge smoke test rather than a production browser. Remaining items above are requirements/backlog, not claims that those features are already implemented.
