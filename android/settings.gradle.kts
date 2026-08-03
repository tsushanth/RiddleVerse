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
include(":app", ":zipline-api", ":game-guest")
include(":game-guest:kotlin")
include(":paywallkit")
project(":paywallkit").projectDir = file("../../PaywallKit-Android/paywallkit")
include(":ratingkit")
project(":ratingkit").projectDir = file("/Users/sushanthtiruvaipati/Documents/GitHub/RatingKit-Android/ratingkit")

include(":crosspromokit")
project(":crosspromokit").projectDir = file("/Users/sushanthtiruvaipati/Documents/GitHub/CrossPromoKit-Android/crosspromokit")
