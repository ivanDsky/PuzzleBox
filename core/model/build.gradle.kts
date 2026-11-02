// Pure-Kotlin shared domain model: the types :core:game, :core:data, and every feature
// compile against. Zero Android, zero Compose, zero external dependencies — it depends
// on nothing, everything may depend on it.
plugins {
    id("org.jetbrains.kotlin.jvm") // version-less: AGP's built-in Kotlin owns the classpath
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
