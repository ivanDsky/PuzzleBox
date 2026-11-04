// Pure-Kotlin shared domain model: the types :core:game, :core:data, and every feature
// compile against. Zero Android, zero Compose, zero external dependencies — it depends
// on nothing, everything may depend on it.
plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
}
