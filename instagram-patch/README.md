# Native Reels patch

Version-pinned DEX patches for Instagram **455.0.0.51.72** (386113969). Native menu and player changes need no overlay, accessibility service, root or modified bootloader.

## Patch boundaries

`patches.json` specifies 12 equal-length replacements in three DEX files, including exact original APK hashes, method fingerprints, instruction offsets and expected bytes:

1. Force native `ClipsViewPagerImpl` (`X/06Sp`) to keep `ViewPager2` user input disabled.
2. Return immediately from the known next/previous Reel commands. Preserve initial positioning for individual shared Reels.
3. Remove Reels from the configured native tab parser, replace its default destination with Home, and compact the fallback tab array.
4. Reject dedicated Reels-tab fragment pushes in `X/04sT`. Valid non-Reels targets retain their original path; an invalid null argument may now fail later than the original assertion.

This does not remove every Reel recommendation, block every alternate entry point, disable all programmatic player positioning or implement native viewing diagnostics. The separate [Home quota](quota/README.md) adds the daily limit and final row-cache guards.

## Inputs, outputs and dependencies

Provide the exact eight APK splits locally in `original/`, matching `patches.json`. Source releases contain only our patch tooling and rules. They contain no original/patched Instagram APK, decompiled Instagram class, third-party JAR, account export or private signing key.

Set `REELGUARD_JDK` and `ANDROID_SDK_ROOT` as needed. JDK 21, Python 3.11+, SDK platform 35 and build-tools 35.0.0 are used. Run from the repository root:

```sh
python3 instagram-patch/scripts/bootstrap_deps.py
```

The dependency manifest pins Maven Central JAR URLs and SHA-256 hashes. `scripts/environment.py` supplies shared host settings; `REEL_DEX_CP` overrides dexlib2/Guava/failureaccess paths. Additional baksmali/util JARs support `ExtractSmali.java`. Apktool/JADX are optional external inspection tools, not shipped dependencies.

From this directory:

```sh
python3 scripts/verify_inputs.py
python3 scripts/build_patch.py
python3 scripts/verify_patch.py
bash scripts/check_semantics.sh
python3 quota/scripts/build.py
```

`dist/` holds the Reels-only APK set; `quota/dist/` holds the final daily-quota APK set. `work/native-test.keystore` is the private local test key shared by both builds. `dist/verification.json` records local hashes, signatures and static checks. All these directories are ignored by Git.

## Verification

Input hashes and expected original bytes must match. The patcher rejects overlapping, out-of-range and length-changing edits. DEX SHA-1/Adler32 fields are repaired. Manifest, resources and native libraries remain byte-identical; APK signing metadata is replaced or removed. Every split uses the same local certificate and 16 KiB native-library alignment.

Independent verification checks all non-signature ZIP entries, exactly permitted DEX changes, package/version, alignment and common split certificates. Nine Python fixtures cover valid edits and rejection cases. `NativeSemantics.java` rejects the official unpatched control, then verifies player/menu postconditions directly in the built APK.

The quota builder first verifies this content against the pinned original APKs. It does not pin a particular developer's re-signed APK hash; another user's local signing key can therefore produce a valid prerequisite.

Android ART verification has passed on an Android 14 device. The final Home refresh fix was confirmed by the user; complete physical verification of every Reels route, shared playback, chats and account-specific tab configuration remains incomplete. Static checks do not establish those behaviors.

## Installation

A new local certificate cannot update official Instagram. Replacing the official installation removes app data, login state and local drafts. APK copies are not account-data backups. Build scripts never uninstall or clear data. Keep the local key private so future locally signed builds can update in place with `adb install-multiple -r`.

The existing same-key update helper is `quota/scripts/install.py`. It requires an already installed Instagram package and checks hashes after updating. Disable the old accessibility blocker during native testing. Re-signing triggers a trusted-caller error in the Helium sandbox service; embedded-browser functionality remains unverified. No signature/integrity-check bypass is included.
