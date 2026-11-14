REEL_PATCH_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Values are shell-quoted by environment.py; no arbitrary command text is evaluated.
eval "$(python3 "$REEL_PATCH_ROOT/scripts/environment.py")"
