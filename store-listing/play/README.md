# RiddleVerse - Google Play listing prep (en-US)

Staged package only. Nothing here has been published to Play. Play only (no other store is touched).
Captured 2026-11-01. Package `com.kreativekoala.riddleverse`, production = versionCode 177.

## Current live listing (read-only API pull; edit deleted, never committed)
Full snapshot: `listing/current_live_listing_2026-11-01.json`.

| Field | Live value |
|---|---|
| Title | RiddleVerse - Brain Training |
| Short | Brain training with 45+ AI puzzle types. Daily rewards & global leaderboards |
| Full | "the ultimate brain training app" (uses "ultimate"), 45+ puzzle types, 10 categories, coins, optional Premium |
| Locales | 17: en-US (default), ar, de-DE, es-ES, es-419, fr-FR, hi-IN, id, it-IT, ja-JP, ko-KR, pl-PL, pt-BR, th, tr-TR, vi, zh-CN |
| en-US images | 8 phone screenshots, icon 512x512, feature graphic 1024x500 |
| Tracks | production 177 (completed), beta 174, alpha 151, internal 164 |
| Ratings count | not exposed by the Publisher API (check Console) |

## What the listing says vs what the app does
- **Hook buried:** the app's headline feature is "Create a Game: describe any game and AI will build it for you" (a playable game in about 2 minutes; verified live on device, it produced "Astro Dodge" from one sentence). The live title and short description say nothing about it; the full description mentions it in the 3rd section.
- **Undersold count:** the app shows 72 puzzle types (51 ready, 21 on demand, 47 offline) vs "45+" in the listing. Categories in the app are Math, English, Memory, Visual, Logic, General (6); the listing names 10 incl. Geography/Audio/Reaction/Trivia, which do not appear as filters in this build.
- **Live screenshots are a problem** (see decisions): they use iPhone device frames (9:41 status bar), one says "FEATURED ON APP STORE", and two promise "Earn when players purchase your content / Get Rewarded" with cash imagery. They also pre-date the current UI.
- **Real, usable hooks not in the listing:** free-text AI game creation with idea chips, community Explore/Trending games, per-game leaderboards, game edit/remix, guest mode, Telegram bot (@Riddleverse_bot, verified to exist), daily reward + streak coins.
- **Not real / not used:** multiplayer does not exist in the app. No social-proof numbers are used because none are verified.
- Repo drift: `android/fastlane/metadata/android/en-US` title/short differ from live ("RiddleVerse" / "...45+ types, daily rewards & leaderboards"); live is the truth.

## Variants (all limits checked programmatically, see `listing/variants.json`)
| | Title (<=30) | Short (<=80) | Angle | Screenshot order (first 3) |
|---|---|---|---|---|
| A | RiddleVerse: Puzzle & AI Games (30) | Train your brain with 70+ puzzle types, or describe a game and let AI build it. (79) | clear benefit | create prompt, AI game playing, home |
| B | RiddleVerse: Build AI Games (27) | Type an idea. AI builds a playable game in minutes. Plus 70+ brain puzzles. (75) | curiosity/hook | AI game playing, create prompt, building |
| C | RiddleVerse: Puzzles & Games (28) | 70+ puzzle types, community-made games, leaderboards and daily rewards. (71) | feature-led | home, categories, AI game playing |

C is "feature-led" because we have no verified rating/install numbers to cite; swap in a real number once the owner confirms one.
Each variant has its own 8 captions (`listing/variants.json`), 8 captioned screenshots (`screenshots/variant_X/`) and a feature graphic. Full descriptions: first 2 lines carry the pitch; each includes the true Premium paragraph ($5.99/month, $24.99/year from `SubscriptionManager.kt`; free plan 10 games/day, 100/month; ads on free plan). No "best/#1", no money claims.

## Assets
- `screenshots/original/` raw captures, 960x2142 (2.23:1, outside Play's ratio limit, so NOT uploadable as-is; includes 2 unused extras).
- `screenshots/variant_{A,B,C}/` 1080x1920 (9:16) captioned PNGs, each under 500 KB. Status bar and gesture pill cropped; app theme colors (violet/grape).
- `graphics/feature_graphic_variant_{A,B,C}_1024x500.png` (new composition, reuses the existing icon art, no new logo).
- Icon: current live icon is 512x512 and byte-identical (sha256) to `fastlane/.../images/icon.jpeg`; reused, PNG copy included.
- `manifest.json` lists every asset (dimensions, sha256, purpose, variant). Regenerate with `build_assets.py` then `make_manifest.py`.
- Capture setup: dev build `com.kreativekoala.riddleverse.dev` 176.0-qa (side-by-side with release), guest account, demo-mode status bar, Do Not Disturb. Caveats: shots 02 shows the app's own timer pill overlapping the SCORE label; the Play build (177) was not installed, so UI could differ slightly from production.

## Upload instructions
Option 1 (script): `python3 stage_listing.py --variant A` is a dry run (default; prints the calls). Add `--diff-live` to compare with the live text through a throwaway edit that is deleted. `--commit` writes the title/short/full text, replaces the 8 phone screenshots and the feature graphic in en-US and commits the edit; it never touches tracks. Do not run `--commit` without owner approval; the change goes live on commit.
Option 2 (Console): Grow > Store presence > Main store listing (or Custom store listing / Store listing experiments), paste text from `variants.json`, upload `variant_X` PNGs in order.
Experiments note: the Publisher API has no store-listing-experiment endpoint, so a harness using the API can only swap the main listing (run variants one after another and compare installs by acquisition date), while true concurrent A/B needs the Console experiments UI.

## API calls (as in `stage_listing.py`)
`edits.insert` -> `edits.listings.patch(en-US)` -> `edits.images.deleteall(phoneScreenshots)` -> `edits.images.upload` x8 -> `edits.images.deleteall(featureGraphic)` -> `edits.images.upload` -> `edits.commit` (or `edits.delete`).

## Owner decisions needed
1. **Live screenshots:** replace them? They show iPhone frames and "Featured on App Store" on a Play listing (likely a Play metadata-policy risk and contradicts the Play-only direction).
2. **Money claims:** the app has an in-app "Earn real cash on RiddleVerse / get paid" banner and the live listing images promise earnings. I left all of it out of the new copy and hid that banner in the shots. Decide whether a creator-payout claim is real, compliant and wanted; it needs separate review.
3. Which variant first (suggest A or B vs the current live as control, one at a time) and whether the harness should use Console experiments instead of sequential swaps.
4. Other 16 locales still say "45+" and old claims; translate after an English winner.
5. Play contact website is `https://www.sendsmiles.biz/` and and a contact phone and email are on the listing (redacted in the committed snapshot because this repo is public); confirm that is intended.
6. In-app name "Waldo Puzzle" (Visual category) is a third-party trademark; consider renaming. Daily-reward coins: balance stayed 0 after claiming as a guest, so the "daily reward" claim is code-verified but not confirmed to credit guests.
