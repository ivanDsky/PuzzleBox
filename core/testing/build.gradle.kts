// Test doubles shared by every module's unit tests (NiA core:testing). Consumers depend on
// it ONLY through testImplementation — no production configuration may reach it.
plugins {
    alias(libs.plugins.puzzlebox.android.library)
}

android {
    namespace = "com.ivandsky.puzzlebox.testing"
}

dependencies {
    // The fakes implement core:data's interfaces (and expose core:game/core:model types).
    api(projects.core.data)
    // MainDispatcherRule: TestDispatcher + JUnit4 TestWatcher in its public signature.
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)
}
