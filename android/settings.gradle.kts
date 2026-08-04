pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "RiddleVerse"
// :zipline-api, :game-guest, :game-guest:kotlin removed 2026-08-03 — their
// directories don't exist on disk (android/zipline-api/, android/game-guest/
// are both gone; only stale .idea/ and .kotlin/ cache metadata remains).
// Gradle 8.x silently tolerated this; Gradle 9.4.1 hard-fails with
// "project directory does not exist". App code depends on the zipline
// runtime as a normal remote dependency (app.cash.zipline:zipline), not
// these local composite modules, so removing the dead includes doesn't
// affect zipline functionality.
include(":app")
include(":paywallkit")
project(":paywallkit").projectDir = file("../../PaywallKit-Android/paywallkit")
include(":ratingkit")
project(":ratingkit").projectDir = file("/Users/sushanthtiruvaipati/Documents/GitHub/RatingKit-Android/ratingkit")

include(":crosspromokit")
project(":crosspromokit").projectDir = file("/Users/sushanthtiruvaipati/Documents/GitHub/CrossPromoKit-Android/crosspromokit")
