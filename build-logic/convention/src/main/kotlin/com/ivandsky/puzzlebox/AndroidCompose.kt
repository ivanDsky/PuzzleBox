package com.ivandsky.puzzlebox

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose for an Android module: compiler plugin, BOM-aligned material3, @Preview
 * annotations in every variant (previews live in main source next to components) with
 * the Layoutlib renderer debug-only, and the Robolectric compose-test harness.
 */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    commonExtension.buildFeatures.compose = true

    dependencies {
        val bom = libs.library("androidx-compose-bom")
        "implementation"(platform(bom))
        "implementation"(libs.library("androidx-compose-material3"))
        "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
        "debugImplementation"(libs.library("androidx-compose-ui-tooling"))

        "testImplementation"(platform(bom))
        "testImplementation"(libs.library("androidx-compose-ui-test-junit4"))
        "testImplementation"(libs.library("androidx-compose-ui-test-manifest"))
    }
}
