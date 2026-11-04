package com.ivandsky.puzzlebox.konsist.core

import java.io.File

object ModuleReader {
    private const val GUARDED = ":konsist"

    fun read(root: File): List<Module> =
        SettingsReader.includes(File(root, "settings.gradle.kts").readText())
            .filter { it != GUARDED }
            .map { path -> module(root, path) }

    private fun module(root: File, path: String): Module {
        val dir = path.removePrefix(":").replace(':', '/')
        val buildFile = File(root, "$dir/build.gradle.kts")
        check(buildFile.isFile) { "Module $path has no build.gradle.kts at $dir" }
        val facts = BuildFileReader.read(buildFile.readText())
        // The path fallback is for JVM modules only; an Android module's package root is its namespace.
        check(facts.namespace != null || !isAndroid(facts.plugins)) {
            "Android module $path declares no namespace in $dir/build.gradle.kts"
        }
        val packageRoot = facts.namespace ?: PackageRootFallback.forJvmModule(path)
        checkNotNull(packageRoot) { "Module $path has no namespace and no package-root fallback" }
        return Module(path, dir, packageRoot, facts.plugins)
    }

    /** `com.android.library`, `puzzlebox.android.feature.impl`, `kotlin.android`: an `android` segment in the id. */
    private fun isAndroid(plugins: Set<String>) = plugins.any { "android" in it.split('.') }
}
