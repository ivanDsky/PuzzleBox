// Pure-Kotlin Tango game module (A16/A7): engine, variant data, versioned codecs,
// renderer model, plugin. No Compose, no Android, no Room.
plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.game)
    implementation(projects.core.model)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.core)
}
