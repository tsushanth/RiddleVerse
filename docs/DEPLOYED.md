# Deployed releases (Play)

## 183.0 (versionCode 183): production, 20% staged rollout, 2026-11-16
- Built from efd127c (tag `play/RiddleVerse-183.0-183`); android/app, backend and web identical to origin/main at upload time.
- Gradle: `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:bundleRelease` passed. The first build failed lint with 11 errors: 5 caused by the i18n wave (plural one-form without a number in fa, tl, hi, pt-BR, fr) fixed, and 6 pre-existing suppressed with a reason each (PR #48). The clean worktree also needs the gitignored `android/gradle.properties` (`android.builtInKotlin=false`, `android.newDsl=false`) and `local.properties`.
- AAB sha256: 7d42dcf28789def4e58a30fc34abf4cc209630f39354178741bdfa7c92662393
- Play production track read back after commit: 183.0 (183) inProgress userFraction 0.2; 182.0 (182) completed.
- Contents: PR #45 (29 UI languages incl. fa ur bn; non-Latin answers accepted by both matchers; RTL arrows; localized daily puzzle, reminders and guest prompts; picker works on Android 8-12) and #46 (version bump). Translations are machine-made and need native review. Puzzle and riddle content is still English.
- Rollback: re-upload 182.0 (182) as the completed production release, or halt the rollout.
