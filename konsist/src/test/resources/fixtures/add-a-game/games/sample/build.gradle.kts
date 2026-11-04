plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
}

dependencies {
    api(projects.core.game)
    implementation(libs.koin.core)
}
