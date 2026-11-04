pluginManagement {
    includeBuild("build-logic")
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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PuzzleBox"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":backend:firebase")
include(":core:designsystem")
include(":core:common")
include(":core:data")
include(":core:game")
include(":core:game-ui")
include(":core:model")
include(":games:zip")
include(":games:zip-ui")
include(":games:queens")
include(":games:queens-ui")
include(":games:tango")
include(":games:tango-ui")
include(":feature:home")
include(":feature:play")
include(":feature:history")
