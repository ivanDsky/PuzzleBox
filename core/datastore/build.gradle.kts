plugins {
    alias(libs.plugins.puzzlebox.android.library)
}

android {
    namespace = "com.ivandsky.puzzlebox.datastore"
}

dependencies {
    // ThemeMode appears in PuzzleBoxPreferences / the data source's setters.
    api(projects.core.model)
    // DataStore<Preferences> is the data source's constructor parameter.
    api(libs.androidx.datastore.preferences)
    // DispatcherProvider: the DataStore's process-lifetime I/O scope.
    implementation(projects.core.common)
    implementation(libs.koin.core)
}
