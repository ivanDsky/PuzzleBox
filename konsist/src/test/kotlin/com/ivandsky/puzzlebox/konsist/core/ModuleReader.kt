package com.ivandsky.puzzlebox.konsist.core

import java.io.File

object ModuleReader {
    private const val GUARDED = ":konsist"

    fun read(root: File): List<Module> {
        val settings = CommentStripper.strip(File(root, "settings.gradle.kts").readText())
        return SettingsReader.includes(settings)
            .filter { it != GUARDED }
            .map { path -> module(root, path) }
    }

    private fun module(root: File, path: String): Module {
        val dir = path.removePrefix(":").replace(':', '/')
        val buildFile = File(root, "$dir/build.gradle.kts")
        check(buildFile.isFile) { "Module $path has no build.gradle.kts at $dir" }
        val facts = BuildFileReader.read(CommentStripper.strip(buildFile.readText()))
        val packageRoot = facts.namespace ?: PackageRootFallback.forJvmModule(path)
        checkNotNull(packageRoot) { "Module $path has no namespace and no package-root fallback" }
        return Module(path, dir, packageRoot, facts.plugins)
    }
}
