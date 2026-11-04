import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.ivandsky.puzzlebox.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.puzzlebox.android.application.asProvider().get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = libs.plugins.puzzlebox.android.application.compose.get().pluginId
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.puzzlebox.android.library.asProvider().get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = libs.plugins.puzzlebox.android.library.compose.get().pluginId
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeatureApi") {
            id = libs.plugins.puzzlebox.android.feature.api.get().pluginId
            implementationClass = "AndroidFeatureApiConventionPlugin"
        }
        register("androidFeatureImpl") {
            id = libs.plugins.puzzlebox.android.feature.impl.get().pluginId
            implementationClass = "AndroidFeatureImplConventionPlugin"
        }
        register("androidRoom") {
            id = libs.plugins.puzzlebox.android.room.get().pluginId
            implementationClass = "AndroidRoomConventionPlugin"
        }
        register("androidRoborazzi") {
            id = libs.plugins.puzzlebox.android.roborazzi.get().pluginId
            implementationClass = "AndroidRoborazziConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.puzzlebox.jvm.library.get().pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
