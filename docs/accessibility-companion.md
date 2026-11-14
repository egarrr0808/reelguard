# Legacy accessibility companion

A local Android prototype that disables the Instagram Reels tab while allowing individual Reels opened from messages to play. A transparent accessibility overlay consumes drags in supported full-screen Reels viewers and forwards stationary single taps. It does not modify Instagram or redistribute its menu items.

## Install and run

Install `build/ReelGuard-debug.apk` on Android 8 or later. Open ReelGuard, read the disclosure, select **Enable accessibility blocker**, and enable **ReelGuard Reels blocker** in Android Settings. Sideloaded apps may require **Allow restricted settings** in their Android App info menu before accessibility can be enabled.

The app displays **BLOCKING INACTIVE** until the Android accessibility service is connected and blocking is enabled. Turning on the app preference alone is insufficient. Enter your own username if you want sessions labelled. This is a manual, unverified label; ReelGuard does not log into Instagram or detect account switches.

The Reels tab retains its original appearance and spacing but does not respond to taps. The previous solid-color cover is removed. Open a Reel from an existing conversation to watch it; vertical and horizontal drags and multitouch in the detected viewer are consumed. Stationary single taps are replayed to Instagram so tap controls remain usable. Standalone shared-player playback is not closed automatically. A detected native feed layout (viewer plus selected Reels tab plus Home tab) requests navigation to Home. This layout heuristic does not verify that a video came from a message. Long presses and drag seeking are not supported. System Back remains available. Detection depends on Instagram exposing known viewer identifiers.

Home, search, profile, ordinary feed videos and DMs should remain usable. The touch overlay should disappear outside Instagram. Disable blocking in ReelGuard or turn off its accessibility service to stop interception.

## Local config and diagnostics

The app maintains `files/reelguard.json` in private storage. Settings are stored in private Android preferences. **Export config + diagnostics JSON** writes a combined snapshot to a location you choose in Android's document picker. It includes:

- Blocking settings, transparent-tab mode, viewer mode and optional manual username.
- Installation/export timestamps, timezone, device model, Android version and Instagram version.
- Observed Instagram sessions: start/end, monotonic observed duration, last observation, interruption reason and per-session blocked attempts.
- Latest Instagram layout diagnostics: relevant resource IDs, rectangles and clickable/selected states; no visible text.
- Lifetime blocked-tab, viewer-detection, blocked-drag and forwarded-tap counters; the last 500 sessions and 300 diagnostic events.
- Optional Android Instagram usage buckets for the last seven calendar days: foreground time, last use, and (Android 10+) visible and foreground-service time.

Enable **Allow Android usage statistics** to include Android's system usage totals. These cover the entire Instagram app, not Reels alone. OS aggregation can return buckets extending beyond the requested dates. Missing permission or OS data is reported rather than guessed.

The app has no network permission. It does not record DMs, passwords, access tokens, arbitrary screen text or other profiles. It cannot obtain complete Instagram account history. Session measurements exist only while accessibility is running; process death may lose up to 15 seconds of unflushed counters. An interrupted session has no invented end timestamp. Wall-clock changes can alter timestamp order, but durations use monotonic time. A manually entered account label is not proof that the observed session belongs to that account.

## Build and checks

Requires Python 3, JDK 21, Android SDK platform 35 and build-tools 35.0.0. No Maven dependencies or downloads are required.

```sh
REELGUARD_JDK=/path/to/jdk21 ANDROID_SDK_ROOT=/path/to/Android/Sdk ./build.sh
adb install --no-incremental -r build/ReelGuard-debug.apk
adb shell am start -n local.reelguard/.MainActivity
```

`build.sh` runs pure Java detection and gesture regression checks, compiles the app, packages it, and verifies APK signing and alignment. Its signing key is a local debug key under `build/`; keep that key to install subsequent builds over the same app without uninstalling. Debug builds are for local testing, not Play publication.

## Known limitations

Detection is conservative and depends on Instagram's accessibility identifiers. Unsupported layouts, translated labels without identifiers, web Instagram, Instagram Lite and hidden nodes are not covered. Only known Reels viewer containers receive a drag blocker; generic video players do not. The transparent tab blocker does not reflow the bottom menu. Removing an actual Instagram menu item requires a different integration inside Instagram; this companion app cannot do that. Controls requiring dragging, long presses, or multitouch inside the covered viewer are intercepted. Comment sheets that retain the viewer underneath may also have their dragging intercepted. Viewer windows resize in place and persist across missing-node transitions for up to 700 ms. Accessibility acts after interface changes; fast opening has exposed an initial-scroll bypass, which is not yet verified as resolved. Do not rely on this prototype for strict blocking. Tap forwarding briefly makes the overlay pass-through, so a perfectly timed extra touch is a possible bypass. This is not strict enforcement. Gesture forwarding requires accessibility gesture capability; re-enable the service if taps stop working after updating. The service is user-disableable and is not tamper-proof parental control. Battery management, accessibility interruption and third-party app updates can stop blocking. Existing banking apps may object to enabled accessibility services.

This is the earlier prototype. Fast-open and refresh bypasses were observed. It is not used by the native patch and is not reliable for strict blocking. Run build commands from the repository root.
