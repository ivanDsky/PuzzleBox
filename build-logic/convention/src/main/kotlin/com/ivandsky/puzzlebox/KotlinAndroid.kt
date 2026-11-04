package com.ivandsky.puzzlebox

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 26
internal const val TARGET_SDK = 37

/**
 * SDK levels and Java 17 for every Android module. Kotlin is AGP 9's built-in Kotlin
 * (no kotlin-android plugin); its JVM target follows [CommonExtension.compileOptions].
 */
internal fun configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = COMPILE_SDK
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * Robolectric-based JVM unit tests: merged resources/manifest on the test classpath plus
 * the shared junit / Robolectric / coroutines-test stack.
 */
internal fun Project.configureAndroidUnitTests(commonExtension: CommonExtension) {
    commonExtension.testOptions.unitTests.isIncludeAndroidResources = true
    dependencies {
        "testImplementation"(libs.library("junit"))
        "testImplementation"(libs.library("robolectric"))
        "testImplementation"(libs.library("kotlinx-coroutines-test"))
    }
}

/** Pure-Kotlin JVM modules: Java 17 bytecode and JVM 17 Kotlin target. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }
}
