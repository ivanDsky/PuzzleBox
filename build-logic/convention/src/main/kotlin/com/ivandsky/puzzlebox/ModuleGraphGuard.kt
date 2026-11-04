package com.ivandsky.puzzlebox

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/** Name of the per-module task that enforces [forbiddenEdgeReason]; `check` depends on it. */
const val CHECK_MODULE_GRAPH_TASK = "checkModuleGraph"

/**
 * Registers [CHECK_MODULE_GRAPH_TASK] on this module and wires it into `check` and into the
 * root of every build: `preBuild` (Android) / `classes` (JVM), so `assembleDebug` and
 * `testDebugUnitTest` fail on a forbidden edge too.
 *
 * Every configuration is inspected — `implementation`, `api`, `testImplementation`,
 * `androidTestImplementation`, `ksp`, … — so a test-only edge is as forbidden as a main one.
 * The edges are read lazily, after the module's `dependencies {}` block has run.
 */
internal fun Project.configureModuleGraphGuard() {
    val from = path
    val guard = tasks.register<CheckModuleGraphTask>(CHECK_MODULE_GRAPH_TASK) {
        group = "verification"
        description = "Fails if $from declares a module dependency the module graph forbids."
        violations.set(
            provider {
                configurations.flatMap { configuration ->
                    configuration.dependencies.withType<ProjectDependency>().mapNotNull { dep ->
                        forbiddenEdgeReason(from, dep.path, configuration.name)?.let { reason ->
                            "$from -> ${dep.path} (configuration '${configuration.name}'): $reason"
                        }
                    }
                }.distinct()
            },
        )
    }
    tasks.matching { it.name in GUARDED_TASKS }.configureEach { dependsOn(guard) }
}

private val GUARDED_TASKS = setOf("check", "preBuild", "classes")

/**
 * The forbidden edges of the module graph (docs/adr/0008-module-graph.md); `null` = allowed.
 * `:app` (the composition root) and `:backend:*` are otherwise unconstrained.
 * The `:konsist` verification family has zero edges in either direction, test configurations
 * included, so its arms sit before the `:core:testing` test-configuration exemption.
 */
internal fun forbiddenEdgeReason(from: String, to: String, configuration: String): String? {
    if (from == to) return null
    val fromPath = from.removePrefix(":").split(":")
    val toPath = to.removePrefix(":").split(":")
    return when {
        fromPath[0] == "konsist" -> "konsist reads sources as files; it has no project dependencies"

        toPath[0] == "konsist" -> "nothing may depend on the verification module :konsist"

        to == ":app" -> "nothing may depend on the composition root :app"

        to == ":core:testing" && !configuration.contains("test", ignoreCase = true) ->
            ":core:testing is reachable only from test configurations"

        fromPath[0] == "core" && toPath[0] != "core" ->
            "core modules may depend on core modules only"

        fromPath[0] == "feature" && toPath[0] == "games" ->
            "features reach games only through core:game / core:game-ui registries"

        fromPath[0] == "feature" && fromPath.getOrNull(2) == "api" &&
            to != ":core:navigation" && to != ":core:model" ->
            "feature api modules may depend on :core:navigation and :core:model only"

        fromPath[0] == "feature" && toPath[0] == "feature" &&
            toPath.getOrNull(2) == "impl" && fromPath[1] != toPath[1] ->
            "feature impl modules are independent; depend on the other feature's :api"

        fromPath[0] == "games" && toPath[0] == "feature" ->
            "games must not know about features"

        fromPath[0] == "games" && toPath[0] == "games" && fromPath[1] != "${toPath[1]}-ui" ->
            "a game module may depend on no other game (only games:x-ui -> games:x)"

        else -> null
    }
}

/** Fails with every collected violation; violation-free modules pass. */
abstract class CheckModuleGraphTask : DefaultTask() {
    @get:Input
    abstract val violations: ListProperty<String>

    @TaskAction
    fun verify() {
        val found = violations.get()
        if (found.isNotEmpty()) {
            throw GradleException(
                "Forbidden module dependencies (see docs/adr/0008-module-graph.md):\n" +
                    found.joinToString("\n") { "  - $it" },
            )
        }
    }
}
