# ReelGuard

Native Android patches for Instagram: remove the Reels tab, disable paging between Reels, and limit Home to **20 unique posts per day**. Designed to preserve playback of individual shared Reels. The native patch uses no accessibility service, overlay or root.

This is an experimental, version-specific project. Currently supported input: Instagram **455.0.0.51.72**, version code **386113969**, arm64, the eight-split set pinned in [patches.json](instagram-patch/patches.json). Other APK variants, even with the same version name, are rejected. No Instagram APKs, decompiled source, signing keys or account data are included.

## Behavior

- Remove Reels from native tab configuration and reject dedicated Reels-tab navigation.
- Disable the known native Reels pager's gestures and next/previous commands. Initial shared-Reel positioning remains available.
- Admit at most 20 unique media posts into Home per local calendar day, with state retained across refreshes, restarts and same-key updates. Native row guards also cover the refreshed row caches.
- The quota counts **posts made available before display**, not posts actually viewed. Prefetch can reserve slots. Previously admitted posts do not spend another slot.
- The allowance is shared across accounts on one installation. Preferences contain the day and up to 20 hashed post IDs. Unknown Home recommendation containers are omitted; Home may become empty after exhaustion.

The Home refresh bypass was reproduced and the subsequent native row-cache fix was confirmed on an Android 14 device. Day rollover, rollback, persistence, concurrent admission and both row-cache formats have automated tests. Physical midnight rollover, the initial allowance with the final row guard, all alternate Reels routes and shared-Reel/chat regression are not fully verified.

## Build locally

Linux build host tested. Requirements: Python 3.11+, JDK 21, Android SDK platform 35, build-tools 35.0.0, and platform-tools for installation. Python dependencies use the standard library. Build scripts never download Instagram.

Set host paths if they differ from your installation:

```sh
export REELGUARD_JDK=/path/to/jdk21
export ANDROID_SDK_ROOT=/path/to/Android/Sdk
```

Supply your own exact original eight APK splits in `instagram-patch/original/`, using the filenames in the manifest. The expected hashes are public; the APKs remain local. The patch scripts deliberately reject other versions rather than applying offsets speculatively.

```sh
python3 instagram-patch/scripts/bootstrap_deps.py
bash scripts/check.sh
cd instagram-patch
python3 scripts/verify_inputs.py
python3 scripts/build_patch.py
python3 scripts/verify_patch.py
bash scripts/check_semantics.sh
python3 quota/scripts/build.py
```

`bootstrap_deps.py` downloads SHA-256-pinned dexlib2, baksmali, util, Guava and failureaccess JARs from Maven Central. JARs remain ignored. `REEL_DEX_CP` can override the three core library paths; exploratory disassembly also needs baksmali/util on its classpath.

The final local APK set is in `instagram-patch/quota/dist/`. The previous Reels-only build stays in `instagram-patch/dist/`. Build artifacts are intentionally excluded from source releases.

## Installation and signing

The build creates a **local test key** in `instagram-patch/work/native-test.keystore`. Keep that private key for future updates. Its password is the public test default `android`; this is not a production signing setup. Never commit or publish the key.

A locally signed build cannot update official Instagram because its signing certificate differs. Replacing the official app removes its local data and drafts; copied APKs do not back up a login session. Installation is a separate manual decision. No build or test script uninstalls Instagram or clears its data.

For an already installed build signed with your same local key:

```sh
python3 instagram-patch/quota/scripts/install.py
```

This uses `adb install-multiple -r`, checks all eight installed hashes and opens Instagram. Set `ANDROID_SERIAL` when selecting a specific connected device. It does not prove that your device's ART verification passed. Disable the legacy ReelGuard accessibility blocker when testing the native patch.

## Limits

- This is a local self-control tool, not tamper-proof enforcement. Clearing app data, reinstalling or advancing the date can reset local state. Backward dates do not grant another allowance.
- Home's quota does not apply to profiles, search or other viewers. It does not globally remove every Reel recommendation or alternate entry route.
- Network pagination may continue after the visible quota is exhausted. No custom quota-exhausted screen is implemented.
- Re-signing triggers a trusted-caller error in Instagram's Helium sandbox service. The main app remains usable on the test device; embedded-browser behavior is unverified.
- Account-specific feature flags and Instagram updates can invalidate the patch. No signature/integrity-check bypass is included.
- Native account-history export and native viewing diagnostics are not implemented. The [legacy accessibility companion](docs/accessibility-companion.md) has separate optional local diagnostics and usage-statistics export, but has known blocking bypasses.

See [native patch details](instagram-patch/README.md) and [quota details](instagram-patch/quota/README.md).

## Contributing and releases

Run `bash scripts/check.sh` for source-only checks; no APK or phone is required. Full native verification requires the exact original inputs and the Android SDK. Before changing version-specific hooks, inspect the target, update preconditions and semantic checks, and test native behavior on a device.

Run `python3 scripts/check_public.py --archive` to produce a source-only archive from the explicit `public-files.txt` allowlist. The check rejects binaries, private working directories, common secrets and workstation paths. CI runs publication checks and source-only tests. Do not attach patched Instagram APKs or private diagnostic exports to issues or releases.

Original project code is MIT-licensed. Instagram and third-party dependencies retain their own ownership and licenses. This project is independent of Meta and Instagram.
