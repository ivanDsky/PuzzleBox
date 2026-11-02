// Pure-Kotlin Queens game module (mirrors games/zip, A16/A3): engine skeleton, variant
// data, versioned codec, wire parser, renderer model, plugin. No Compose, no Android,
// no Room — and zero :core:* edits. Walking skeleton (queens-01): no rules, solver,
// session lifecycle, or feed — those land in queens-02+ against the interfaces pinned
// here (QueensPuzzle, QueensRulesVerdict name).
plugins {
    id("org.jetbrains.kotlin.jvm") // version-less: AGP's built-in Kotlin owns the classpath
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":core:game"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    // Plugin registration (ADR-0001): one Koin module, collected by `app`.
    implementation(libs.koin.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
