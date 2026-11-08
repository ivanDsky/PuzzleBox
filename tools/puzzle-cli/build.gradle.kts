// Runnable pure-JVM developer tool (ADR-0021): `validate` (and later `solve`) over
// `puzzlebox <game> v<n>` text, run from installDist without Gradle. Ships nowhere.
plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

dependencies {
    implementation(projects.core.game)
    implementation(projects.games.queens)
    implementation(projects.games.zip)
    implementation(projects.games.tango)
    implementation(projects.games.sudoku)
    implementation(projects.games.strands)
    implementation(libs.kotlinx.serialization.json)
}

application {
    applicationName = "puzzle-cli"
    mainClass = "com.ivandsky.puzzlebox.tools.puzzlecli.PuzzleCliKt"
}

tasks.withType<Test>().configureEach {
    // §9.3: the loader reads canonical fixtures under the repo root (used by M5-03-b).
    systemProperty("notation.fixtures", rootDir.absolutePath)
    // The loader and NotationDocTest read these files directly, so Gradle must re-run on any edit.
    inputs.files(rootDir.resolve("docs/puzzle-notation.md")).withPropertyName("notationDoc")
    inputs.files(fileTree(rootDir.resolve("games")) { include("*/src/test/resources/notation/*.txt") })
        .withPropertyName("notationFixtures")
}
