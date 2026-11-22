# Deployed releases (Play)

## 185.0 (versionCode 185): production, 20% staged rollout, 2026-11-22
- Built from main `6c94781` (squash merge of #60; the tree built for the AAB, `android/` `153f1cc`, equals `origin/main`); tag `play/RiddleVerse-185.0-185`. Signed with the upload key (cert SHA-256 `edd13783...52f7`, same as the Pixel copy).
- Fix: `Purchases.configure` moved off the main thread (`RevenueCatBootstrap.kt`); it was the top ANR cluster in Play vitals (7 issues, 25 reports, builds 168-184; stacks blocked in `MyApplication.onCreate` at the RevenueCat configure call). `FailureReporter` pending-crash read moved to its worker thread. Not changed: PuzzleEvents, Funnel, TikTok init, Firebase init provider samples.
- Verified: `:app:compileDebugKotlin`, `:app:compileDebugUnitTestKotlin`, `:app:assembleRelease` and `:app:bundleRelease` succeed. NOT run: the unit tests themselves, and the device smoke test (the Pixel was unplugged before it could run; the 185 release APK was installed in place over 183 on the Pixel, not exercised). No ANR change measured yet: Play vitals volume is about 60 users a day, so judge over a week.
- AAB sha256: 307c08fad97de29e561708cb1c5e0aa95ca979a944100386384bdf608a9d7a9d
- Play production track read back after commit: 185.0 (185) inProgress userFraction 0.2; 184.0 (184) completed. Changelog: "Faster, smoother app start."
- Rollback: halt the rollout in Play Console (184 stays completed for everyone else), or re-upload 184 content as a new versionCode.

## 184.0 (versionCode 184): production, 100% since 2026-11-19 (20% staged rollout from 2026-11-17)
- Built from main `39f46ec` (merge of #54 on top of #51 i18n sweep, #52 model-name neutralisation, #53 hard-coded-string guard); tag `play/RiddleVerse-184.0-184`. The built `android/` tree equals `origin/main` (later commits touch docs only).
- Gradle: `:app:testDebugUnitTest` and `:app:bundleRelease` on the built tree; `:app:lintDebug` on the previous tree 8a5e365 (identical app code, version bump only), all exit 0.
- Device check (Pixel 9 Pro, in-place update over 183, upload-key signature, 10 locales en ar ja ru de hi uk es tr th, 5 screens each): PASS, 0 crash/ANR/died, no raw tokens, Arabic mirrors. Checked on 8a5e365 (same code, versionCode 183).
- AAB sha256: 7232d1d3277293966876558f7c7c7f69d5bfa4d32fd039a13e78927bd1052e18
- Play production track read back after commit: 184.0 (184) inProgress userFraction 0.2; 183.0 (183) completed. Not raised above 20%.
- Raised to 100% on 2026-11-19 on the owner's instruction (Play Developer API edit: 184.0 (184) set to completed with no userFraction, validated, committed). Play production track read back afterwards: 184.0 (184) completed, no other release listed. Not done at promotion: a device check of the Play-signed build (the Pixel copy carries the upload-key signature, so the check above is the only device evidence) and a crash or ANR rate review (the Play vitals query returned an error). A completed release cannot be halted: roll back by shipping a fix as a new version code or re-uploading 183.0 (183) as the completed release.
- Contents: UI text moved from code into string resources (1627 strings, 29 locales, machine translations needing native review), a GPT-vendor mention removed from the Geography feature list. Changelog: "More of the app is now translated into your language."
- Rollback: halt the rollout in Play Console, or re-upload 183.0 (183) as the completed production release.

## 183.0 (versionCode 183): production, 20% staged rollout, 2026-11-16
- Built from efd127c (tag `play/RiddleVerse-183.0-183`); android/app, backend and web identical to origin/main at upload time.
- Gradle: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:bundleRelease` passed. The first build failed lint with 11 errors: 5 caused by the i18n wave (plural one-form without a number in fa, tl, hi, pt-BR, fr) fixed, and 6 pre-existing suppressed with a reason each (PR #48). The clean worktree also needs the gitignored `android/gradle.properties` (`android.builtInKotlin=false`, `android.newDsl=false`) and `local.properties`.
- AAB sha256: 7d42dcf28789def4e58a30fc34abf4cc209630f39354178741bdfa7c92662393
- Play production track read back after commit: 183.0 (183) inProgress userFraction 0.2; 182.0 (182) completed.
- Contents: PR #45 (29 UI languages incl. fa ur bn; non-Latin answers accepted by both matchers; RTL arrows; localized daily puzzle, reminders and guest prompts; picker works on Android 8-12) and #46 (version bump). Translations are machine-made and need native review. Puzzle and riddle content is still English.
- Rollback: re-upload 182.0 (182) as the completed production release, or halt the rollout.
