// Test-only module: structure rules over the whole repo's Kotlin sources. Depends on no
// project module (rules read files, not classes), so it never joins the app's module graph.
plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
}

dependencies {
    testImplementation(libs.konsist)
}

// Superset of everything a rule may scan, so editing any source or build script re-runs the
// rules while build output never does. Passed to the test JVM so the census (ticket 06) can
// assert that the files it scans are covered by these inputs.
val scannedIncludes = listOf(
    "**/src/**/*.kt",
    "**/*.gradle.kts",
    "settings.gradle.kts",
    "konsist/src/test/resources/**",
)
val scannedExcludes = listOf(
    "**/build/**",
    ".gradle/**",
    "build-logic/**/build/**",
)

tasks.test {
    maxHeapSize = "1g"
    systemProperty("puzzlebox.repoRoot", rootProject.projectDir.absolutePath)
    systemProperty("puzzlebox.inputIncludes", scannedIncludes.joinToString(","))
    systemProperty("puzzlebox.inputExcludes", scannedExcludes.joinToString(","))
    inputs.files(
        fileTree(rootProject.projectDir) {
            include(scannedIncludes)
            exclude(scannedExcludes)
        },
    ).withPropertyName("scannedSources").withPathSensitivity(PathSensitivity.RELATIVE)
}
