// Pure-Kotlin Tango game module (A16/A7): engine, variant data, versioned codecs,
// renderer model, plugin. No Compose, no Android, no Room. games/zip build template.
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
    implementation(libs.koin.core)

    testImplementation(libs.junit)
}
