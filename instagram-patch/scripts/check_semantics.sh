#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/java-env.sh"
python3 -m unittest discover -s "$REEL_PATCH_ROOT/scripts" -p 'test_*.py'
"$REEL_PATCH_JDK/bin/javac" -cp "$REEL_DEX_CP" -d "$REEL_PATCH_ROOT/work" "$REEL_PATCH_ROOT/scripts/NativeSemantics.java"
"$REEL_PATCH_JDK/bin/java" -Xmx2g -cp "$REEL_PATCH_ROOT/work:$REEL_DEX_CP" NativeSemantics "$REEL_PATCH_ROOT/original/base.apk" "$REEL_PATCH_ROOT/dist/base.apk"
