# Claude handoff — Remaining SAMP-Mobile integration

## Session health gate (must run before edits)

Before touching code, inspect the current branch tip, working tree, last 10 commits, latest GitHub Actions runs, and the private build guard. Do not assume a previous chat's report is current. If the agent shows repeated unexplained/prewritten file changes, duplicated edits, branch/head mismatches, or conflicting concurrent edits: stop implementation, preserve a checkpoint, report the anomaly with exact SHAs and paths, save a handoff/checkpoint, and restart in a fresh Claude session before making broad changes.

## Repository and hard constraints

Continue the existing branch **opencef-integration-landscape** in yyouki/SAMP-Mobile. Never modify or merge changes into **main**. Preserve current commits; do not recreate the project or reset/rebase the branch.
Related private build configuration repository: yyouki/samp-mobile-private-build. Also inspect yyouki/Alyn-samp-v17_1 only when access is available. Do not put secrets, signing keys, Firebase config, or credentials in source.
The user works from a phone and relies on GitHub Actions; don't require local Android Studio or assume the user can run Gradle locally.
The source branch has recently compiled successfully on GitHub Actions. That is not proof of runtime behavior; no physical-device test has been performed.
The private build repository has a guard that rejects APKs containing libGTASA.so and GTA game-data paths. Do not weaken or remove that guard to force a green APK. The Alyn README says the client requires GTA:SA Android 2.10 game data, but does not establish permission to redistribute Rockstar's library or game assets. Never label such rights as verified just because the code source is public.
OpenCEF must retain the local-bundled-asset restriction until an explicit origin allowlist and threat model are implemented.

## Required working style

1. Produce a read-only capability matrix for the user’s requested features: status (present/partial/missing), concrete source paths/callbacks, evidence, blockers, and test plan.
2. Implement in small coherent slices; one feature end-to-end per commit or a small atomic batch. Add a focused test where practical and run the existing GitHub Actions workflow after each slice.
3. Do not add decorative switches that only save preferences. Every toggle must change a real native/client behavior or be clearly marked as not available.
4. Keep source changes traceable and avoid repeated one-file pushes that start many parallel CI jobs. Use a stable checkpoint, review diffs, then one coherent commit per slice.
5. Do not claim a build, test, UI runtime, gameplay, or asset rights verification unless a concrete result supports the claim.
6. Keep the visual direction close to classic SA-MP PC menus/scoreboard/dialogs: compact tables, sections, separators, clear labels, restrained charcoal/gray surfaces, and a restrained red accent. Avoid generic SaaS cards/HUD styling and avoid yellow as the main accent.

## Main objective: PC-like UI and attachment/toy editor

Audit the actual in-game native UI under app/src/main/cpp/samp/gui and game attachment UI in app/src/main/java/com/samp/mobile/game/ui/AttachEdit.java and app/src/main/res/layout/attachedit.xml.
The in-game PlayerTabList and DialogManager already exist; verify and improve them instead of adding a duplicate Android scoreboard. Connect physical keyboard TAB/ESC and relevant navigation keys to the actual native UI state only if the existing input pipeline supports it; document any required JNI/native changes.
Complete the attachment/toy editor so it behaves like a PC-style attachment editor rather than only looking like one:
- axis/property selection for position X/Y/Z, rotation X/Y/Z and scale;
- clear selected-property state and fine increment/decrement;
- expose live numeric values when the native layer can safely supply them;
- support physical keyboard controls without breaking touch;
- Save must commit native attachment state and Close/Exit must have verified cancel/restore behavior;
- enumerate/select the actual attached toy/object slot if the native source provides a stable API; do not invent slot ids or silently edit the wrong object.
Reuse the existing native AttachClick/Save/Exit bridge unless inspection proves it is insufficient. If numeric values or toy slots are unavailable, explain the native API needed and implement only the safe portion instead of fabricating values.

## Remaining requested features to audit and integrate

- launcher/game UI replacement or update while preserving the existing branch work;
- AML integration/update;
- MonetLoader compatibility on both armeabi-v7a and arm64-v8a;
- CLEO integration for 32-bit and 64-bit only after compatible open-source loader/license/build provenance is verified;
- Android system keyboard and external keyboard/mouse / Mobilador input;
- SA-MP 0.3DL/model-download protocol support, with protocol compatibility demonstrated rather than a version label change;
- voice-chat enable/disable, push-to-talk and toggle-to-talk modes, microphone/incoming volume;
- FPS counter and FPS limit, fullscreen, auto-reconnect, JPatch, modified-data mode, GPS;
- chat log font size, visible line count, x/y position and dimensions, timestamps;
- gyro only if game/native camera controls provide a safe and testable integration;
- data Full/Lite selection through verified manifests/import paths without repackaging GTA content;
- remove the hosted-server feed/entry if it is not a real user-facing function;
- OpenCEF integration using safe bundled pages and a usable JS/native bridge;
- F8 screenshot, Tab/Esc, PC-like dialog and scoreboard behavior;
- missing passenger control, block/parry-style game HUD action, and GTA-style settings. Avoid changing actual gameplay key mappings without tracing the matching native game action.
- weapon-icon requests are limited to in-game HUD display and must not introduce unrelated real-world weapon content.

Start by distinguishing features that already work from ones that merely write preferences or have empty stubs. Relevant known paths:
- app/src/main/java/com/samp/mobile/game/SAMP.java includes F8 screenshot capture, OpenCEF test overlay, and empty showTab/hideTab/setTab/clearTab methods; the real native PlayerTabList is in app/src/main/cpp/samp/gui/samp_widgets/playertablist.cpp.
- app/src/main/cpp/samp/gui/samp_widgets/dialogs/dialog.cpp contains the native SA-MP style dialog.
- app/src/main/cpp/samp/settings.cpp and app/src/main/cpp/samp/settings.h map native-readable settings.ini values.
- app/src/main/cpp/samp/gui/uisettings.cpp consumes native chat dimensions/count/font.
- app/src/main/java/com/samp/mobile/game/ui/AttachEdit.java and app/src/main/res/layout/attachedit.xml control the attachment editor.
- app/src/main/cpp/samp/net/netgame.cpp handles the SA-MP network state machine; use real protocol state rather than a launcher-only mock.
- libGTASA.so, GTA text/assets and related game data are proprietary unless permission and provenance establish otherwise.

## Build and distribution/test gate

Run the source GitHub Actions workflow for native tests, OpenCEF unit tests, Android instrumentation-test compilation, and assembleDebug. Distinguish compile success from device runtime tests. Add artifact upload only in a way that does not expose unverified/proprietary GTA assets. A UI-preview APK without the game engine is acceptable only if it is explicitly labelled as a preview and never presented as a playable client. To produce a playable APK, identify a legitimate model in which the user supplies their own compatible GTA:SA 2.10 files; do not bypass asset guards or claim that a checksum proves redistribution rights.

## Deliverables

Commit source changes only to opencef-integration-landscape. Return:
- a feature matrix with paths and actual implementation status;
- commit SHAs and changed paths;
- Actions URLs and per-step results;
- any handoff/blockers with exact native API or upstream dependency needed;
- APK link only if the exact artifact exists, has been verified for the intended scope, and is safe to distribute.


## Current checkpoint (2026-10-21)

- Source branch head before this keyboard input slice: `ed03be99609b199b1e3270fc40d476af58390d4a`.
- Connection settings now feed the native CNetGame host/port rather than the previous hard-coded endpoint.
- The fullscreen preference and native auto-reconnect behavior are wired through the launcher and native connection state. A server-ban response no longer loops retries.
- The attachment editor UI now uses a compact dark panel with property buttons for position X/Y/Z, rotation X/Y/Z, scale, plus/minus and save/close. It remains a **PC-inspired touch editor**, not a pixel-identical PC recreation, and it still needs runtime verification for native save/restore semantics and selection of the exact attached slot.
- The current slice adds hardware-keyboard shortcuts while the editor is visible: arrows/A/D adjust the selected property, Tab/Shift+Tab changes property, X/Y/Z selects position, S selects scale, 1/2/3 selects rotation, Enter saves and Escape closes. Touch controls remain available. Keyboard shortcuts do not supply live numeric transform values; that requires a safe native read-back API.
- A candidate external-runtime path was added in `ed03be99609b199b1e3270fc40d476af58390d4a`: the private test APK is intended to omit GTA game assets and attempt to load a user's installed GTA:SA Android 2.10 library. This path is not verified on physical Android hardware; Android linker/package visibility restrictions may prevent loading another app's native library, and the user must import their own licensed game data.
- Latest source Actions run for that external-runtime change: https://github.com/yyouki/SAMP-Mobile/actions/runs/39504286387 (build and compile tests succeeded; no runtime/device test).
- Private workflow profile-filter attempt `a2062266b9d2d88ca9f7502f590bda3ee7e0b0af` still failed the non-optional archive guard because GTA assets and `libGTASA.so` remained in the APK. Keep the guard unchanged. Replace Gradle excludes with an allowlisted staged asset/JNI source directory inside the private Actions workspace, then check the final APK archive and signer before uploading it.
- No Claude connector/action is configured for this session. This file is the direct handoff entry point to provide to Claude Code; do not claim the task was delegated automatically.

