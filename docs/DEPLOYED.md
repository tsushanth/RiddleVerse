# Deployed releases (Play)

## 184.0 (versionCode 184): production, 20% staged rollout, 2026-11-17
- Built from main `39f46ec` (merge of #54 on top of #51 i18n sweep, #52 model-name neutralisation, #53 hard-coded-string guard); tag `play/RiddleVerse-184.0-184`. The built `android/` tree equals `origin/main` (later commits touch docs only).
- Gradle: `:app:testDebugUnitTest` and `:app:bundleRelease` on the built tree; `:app:lintDebug` on the previous tree 8a5e365 (identical app code, version bump only), all exit 0.
- Device check (Pixel 9 Pro, in-place update over 183, upload-key signature, 10 locales en ar ja ru de hi uk es tr th, 5 screens each): PASS, 0 crash/ANR/died, no raw tokens, Arabic mirrors. Checked on 8a5e365 (same code, versionCode 183).
- AAB sha256: 7232d1d3277293966876558f7c7c7f69d5bfa4d32fd039a13e78927bd1052e18
- Play production track read back after commit: 184.0 (184) inProgress userFraction 0.2; 183.0 (183) completed. Not raised above 20%.
- Contents: UI text moved from code into string resources (1627 strings, 29 locales, machine translations needing native review), a GPT-vendor mention removed from the Geography feature list. Changelog: "More of the app is now translated into your language."
- Rollback: halt the rollout in Play Console, or re-upload 183.0 (183) as the completed production release.

## 183.0 (versionCode 183): production, 20% staged rollout, 2026-11-16
- Built from efd127c (tag `play/RiddleVerse-183.0-183`); android/app, backend and web identical to origin/main at upload time.
- Gradle: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:bundleRelease` passed. The first build failed lint with 11 errors: 5 caused by the i18n wave (plural one-form without a number in fa, tl, hi, pt-BR, fr) fixed, and 6 pre-existing suppressed with a reason each (PR #48). The clean worktree also needs the gitignored `android/gradle.properties` (`android.builtInKotlin=false`, `android.newDsl=false`) and `local.properties`.
- AAB sha256: 7d42dcf28789def4e58a30fc34abf4cc209630f39354178741bdfa7c92662393
- Play production track read back after commit: 183.0 (183) inProgress userFraction 0.2; 182.0 (182) completed.
- Contents: PR #45 (29 UI languages incl. fa ur bn; non-Latin answers accepted by both matchers; RTL arrows; localized daily puzzle, reminders and guest prompts; picker works on Android 8-12) and #46 (version bump). Translations are machine-made and need native review. Puzzle and riddle content is still English.
- Rollback: re-upload 182.0 (182) as the completed production release, or halt the rollout.
