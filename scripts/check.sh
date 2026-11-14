#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source instagram-patch/scripts/java-env.sh
mkdir -p build/test-classes
"$REEL_PATCH_JDK/bin/javac" --release 8 -d build/test-classes src/local/reelguard/ReelRules.java src/local/reelguard/TapGate.java tests/ReelRulesTest.java tests/TapGateTest.java instagram-patch/quota/src/local/reelguard/quota/DayQuota.java instagram-patch/quota/tests/DayQuotaTest.java
"$REEL_PATCH_JDK/bin/java" -cp build/test-classes ReelRulesTest
"$REEL_PATCH_JDK/bin/java" -cp build/test-classes TapGateTest
"$REEL_PATCH_JDK/bin/java" -cp build/test-classes DayQuotaTest
python3 -m unittest discover -s instagram-patch/scripts -p 'test_*.py'
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 instagram-patch/quota/tests/test_helper.py
printf 'PASS source-only checks; no Instagram APK or connected device required\n'
