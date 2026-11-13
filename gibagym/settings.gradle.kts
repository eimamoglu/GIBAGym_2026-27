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
    }
}

rootProject.name = "GIBAGym"

include(":app")

include(":core:model")
include(":core:engine")
include(":core:ai")
include(":core:ble")
include(":core:ui")

include(":feature:onboarding")
include(":feature:calibration")
include(":feature:training")
include(":feature:history")
include(":feature:settings")
