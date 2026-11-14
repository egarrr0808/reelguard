# Native Home quota: 20 unique posts per day

This adds a daily Home-feed admission limit to Instagram 455.0.0.51.72 (386113969), on top of the existing native Reels/menu patch. It uses no accessibility service or overlay.

## Behavior

- The first 20 distinct media IDs admitted into Home reserve that day's slots before rendering. This counts available posts, **not posts actually viewed**. Network prefetch can reserve slots before scrolling reaches them.
- An already admitted post can appear again without spending another slot. Carousel children do not add slots when Home supplies their parent media as a single post.
- State is shared across accounts on this installation. It persists across refreshes, process death and in-place updates.
- A later local calendar date starts a new allowance. Earlier dates do not reset it. This is a local self-control mechanism; changing time zones/clock forward, clearing app data or reinstalling can reset local state.
- State lives in Instagram-private `reelguard_home_quota_v1` preferences: `limit`, calendar `day`, and at most 20 SHA-256 `post_hashes`. No usernames, messages, tokens or raw media IDs are exported.
- Raw model insertion and all 18 visible-list assignment sites are guarded. Both native row caches also have a final prefix cap in Home getCount and RecyclerView getItemCount; the first unadmitted media row ends the visible feed, including after refresh. Model guards activate only for concrete Home model `X.06qX`; row guards activate only for concrete Home adapter `X.06qW`. Other models/adapters pass through unchanged.
- Unknown non-media Home containers are omitted, including nested recommendation widgets. This may remove Home widgets in addition to posts. Stories outside this model are unaffected by these hooks; real signed-in verification remains required.
- After exhaustion, Home can end or become empty on refresh if Instagram returns entirely new posts. No custom end-of-quota message is implemented. Pagination requests are not disabled, so Instagram may still fetch posts that the model refuses.
- Chats, profiles and search are outside this quota. The existing shared-Reel playback/paging patch remains in the base. Following a post into a different viewer is outside the Home model quota.

## Build and checks

```
python3 tests/test_helper.py
python3 scripts/build.py
```

The build verifies the locally signed native base against the hash-pinned original APKs and reviewed exact-change rules, preserves the prior Reels patch, injects only quota guards and a helper DEX, checks every modified DEX's original methods and effective exception handlers, and checks all other resources for byte identity. It signs with the existing local key and copies the existing signed splits. Original base/splits in `../dist` remain a rollback set.

`tests/DayQuotaTest.java` covers pure immutable state. `tests/test_helper.py` runs the production helper with fixture Android preferences and version-pinned class names: bulk admission, refresh, process restoration, non-Home identity, unknown items, refreshed native row caches, day rollover, backward dates, failed commits and concurrent admission. These do not substitute for real feed testing.

Install all eight `dist/*.apk` using `adb install-multiple -r`. Do not uninstall or clear data. The update requires the previously installed local signing key. Build verification and Android ART verification do not prove signed-in Instagram behavior.

## Validation status

Policy/helper tests pass. Initial full comparison verified 934,622 original methods unchanged apart from the reviewed guards. Android ART verified the complete unsigned APK on the connected Android 14 phone with `--abort-on-hard-verifier-error`, exit 0. A background Helium sandbox service also emitted a trusted-caller signing error after re-signing; the main Instagram process remained alive. Embedded-browser behavior is not verified. Device-specific logs and working evidence stay local and are excluded from source releases. Signed-in state restored at 20/20 after the row-guard update and a 1626 ms cold restart. Three refresh attempts and ten fast swipes left the counter unchanged, and the UI hierarchy showed only the stories tray as scrollable. The user confirmed that Home refresh no longer reveals new posts. Shared-Reel/chat regression, initial first-day allowance with the final row guard, actual widget behavior and physical midnight rollover remain unverified.
