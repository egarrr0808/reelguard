#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
REELGUARD_JDK="${REELGUARD_JDK:-$HOME/.local/opt/temurin-jdk21}"
export JAVA_HOME="$REELGUARD_JDK"
export PATH="$JAVA_HOME/bin:$PATH"
TOOLS="$ANDROID_SDK_ROOT/build-tools/35.0.0"
PLATFORM="$ANDROID_SDK_ROOT/platforms/android-35/android.jar"
mkdir -p build/classes build/test-classes build/generated build/dex
javac --release 8 -d build/test-classes src/local/reelguard/ReelRules.java src/local/reelguard/TapGate.java tests/ReelRulesTest.java tests/TapGateTest.java
java -cp build/test-classes ReelRulesTest
java -cp build/test-classes TapGateTest
"$TOOLS/aapt2" compile --dir res -o build/resources.zip
"$TOOLS/aapt2" link -o build/resources.apk --manifest AndroidManifest.xml -I "$PLATFORM" --java build/generated --min-sdk-version 26 --target-sdk-version 35 --version-code 4 --version-name 0.3.1 build/resources.zip
find src build/generated -name '*.java' -print > build/sources.txt
javac -source 8 -target 8 -Xlint:-options -classpath "$PLATFORM" -d build/classes @build/sources.txt
find build/classes -name '*.class' -print > build/classes.txt
"$TOOLS/d8" --lib "$PLATFORM" --min-api 26 --output build/dex @build/classes.txt
cp build/resources.apk build/unsigned.apk
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/unsigned.apk','a') as apk:
    apk.write('build/dex/classes.dex','classes.dex')
PY
"$TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
if [[ ! -f build/debug.keystore ]]; then
    keytool -genkeypair -keystore build/debug.keystore -storepass android -keypass android -alias androiddebugkey -dname 'CN=ReelGuard Debug' -keyalg RSA -keysize 2048 -validity 3650 >/dev/null 2>&1
fi
"$TOOLS/apksigner" sign --ks build/debug.keystore --ks-pass pass:android --key-pass pass:android --out build/ReelGuard-debug.apk build/aligned.apk
"$TOOLS/apksigner" verify build/ReelGuard-debug.apk
"$TOOLS/zipalign" -c 4 build/ReelGuard-debug.apk
printf 'PASS APK build, signature, alignment\n'
