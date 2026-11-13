package com.ivandsky.puzzlebox

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 26
internal const val TARGET_SDK = 37

/**
 * SDK levels and Java 17 for every Android module. Kotlin is AGP 9's built-in Kotlin
 * (no kotlin-android plugin). The Kotlin JVM target is also pinned on the compilation-level
 * options: KSP reads those, and unset they default to the daemon JDK (25), which Kotlin 2.2
 * cannot target and logs a fallback warning for.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = COMPILE_SDK
        defaultConfig.minSdk = MIN_SDK
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        target.compilations.configureEach { compilerOptions.configure { jvmTarget.set(JvmTarget.JVM_17) } }
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
    configureTestJvm()
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
    configureTestJvm()
}

/**
 * Unit-test compilations opt into coroutines-test's experimental API once, here, instead of
 * per file; main compilations stay strict. Test JVMs (JDK 25) allow native access so
 * Robolectric's conscrypt load does not warn about restricted methods.
 */
private fun Project.configureTestJvm() {
    // Unit-test compile tasks: compileTestKotlin (JVM), compile<Variant>UnitTestKotlin (Android).
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
        if (name == "compileTestKotlin" || name.endsWith("UnitTestKotlin")) {
            compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
    tasks.withType<Test>().configureEach {
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}
