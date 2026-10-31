// Pure-Kotlin contracts module (A16): zero Android, zero Compose, zero external
// dependencies — every game (games/<id>) and both UI layers compile against only this.
// Kotlin JVM is applied version-less: AGP 9's built-in Kotlin already puts the plugin
// on the classpath (same embedded 2.2.10 compiler the Android modules use).
plugins {
    id("org.jetbrains.kotlin.jvm")
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
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
