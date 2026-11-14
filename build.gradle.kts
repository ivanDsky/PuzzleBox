plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}

// Parallel project execution lets other modules' test JVMs run beside :app's Robolectric e2e
// tests, which wait on real-time Compose timeouts and fail (ComposeTimeoutException,
// ClosedScopeException) when starved of CPU. :app's tests start only after every other test
// task in the build has finished; compilation and the other modules' tests still overlap.
val appProject = project(":app")
appProject.tasks.withType<Test>().configureEach {
    mustRunAfter(
        provider {
            subprojects.filter { it != appProject }.flatMap { it.tasks.withType<Test>() }
        },
    )
}

// CI runs the unit tests as two parallel jobs (ADR-0023): `testUi` covers :app and :feature:*,
// `testCore` every other module. `test` still runs them all.
listOf("testUi" to true, "testCore" to false).forEach { (name, ui) ->
    tasks.register(name) {
        group = "verification"
        description = "Runs the unit tests of " + if (ui) ":app and :feature:*." else "every module outside :app and :feature:*."
        dependsOn(
            provider {
                subprojects
                    .filter { (it.path == ":app" || it.path.startsWith(":feature:")) == ui && "test" in it.tasks.names }
                    .map { it.tasks.named("test") }
            },
        )
    }
}
