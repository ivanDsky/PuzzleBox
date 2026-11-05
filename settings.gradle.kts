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
include(":core:database")
include(":core:datastore")
include(":core:domain")
include(":core:game")
include(":core:game-ui")
include(":core:model")
include(":core:navigation")
include(":core:network")
include(":core:testing")
include(":games:zip")
include(":games:zip-ui")
include(":games:queens")
include(":games:queens-ui")
include(":games:tango")
include(":games:tango-ui")
include(":games:strands")
include(":games:strands-ui")
include(":feature:home:api")
include(":feature:home:impl")
include(":feature:play:api")
include(":feature:play:impl")
include(":feature:history:api")
include(":feature:history:impl")
include(":feature:profile:api")
include(":feature:profile:impl")
include(":konsist")
