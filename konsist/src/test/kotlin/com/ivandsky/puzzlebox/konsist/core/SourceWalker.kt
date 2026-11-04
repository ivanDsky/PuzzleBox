package com.ivandsky.puzzlebox.konsist.core

import com.lemonappdev.konsist.api.container.KoScope
import java.io.File

object SourceWalker {
    private val SOURCE_DIRS = listOf("kotlin", "java")
    private val EXCLUDED_ROOT_DIRS = setOf("konsist", "build-logic")
    private val EXCLUDED_ANYWHERE = setOf("build")

    /** The walked files and the one Konsist scope they were parsed into. */
    class Walk(val files: List<SourceFile>, val scope: KoScope)

    /** Kotlin files of every module's src/<set>/{kotlin,java}, sorted by path; each file has exactly one module. */
    fun walk(root: File, modules: List<Module>): Walk {
        val owned = modules.flatMap { candidates(root, it) }
            .distinct()
            .filterNot(::isExcluded)
            .mapNotNull { path -> owned(path, modules) }
            .sortedBy { it.path }
        val scope = KonsistScopes.over(root, owned.map { it.path })
        val koByPath = scope.files.associateBy { KonsistScopes.pathUnder(root, it) }
        val files = owned.map { (path, module, sourceSet) ->
            val ko = koByPath[path] ?: error("Konsist did not parse $path")
            val text = File(root, path).readText()
            SourceFile(
                path, module, sourceSet, SourceSetKind.of(sourceSet), ko.packagee?.name.orEmpty(), ko,
                text, CommentStripper.strip(text), CommentStripper.code(text),
            )
        }
        return Walk(files, scope)
    }

    /** Whole-segment prefix, longest dir wins, so "games/zip-ui/…" never lands in "games/zip". */
    fun moduleFor(path: String, modules: List<Module>): Module? =
        modules.filter { path.startsWith(it.dir + "/") }.maxByOrNull { it.dir.length }

    /** Segment test on a root-relative path; a prefix test would also hit "builder/" or "konsistent/". */
    fun isExcluded(path: String): Boolean {
        val segments = path.split('/')
        return segments.first() in EXCLUDED_ROOT_DIRS || segments.dropLast(1).any { it in EXCLUDED_ANYWHERE }
    }

    private data class Owned(val path: String, val module: Module, val sourceSet: String)

    private fun candidates(root: File, module: Module): List<String> =
        File(root, module.dir + "/src").listFiles { f -> f.isDirectory }.orEmpty().flatMap { setDir ->
            SOURCE_DIRS.flatMap { lang ->
                File(setDir, lang).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }
        }.map { it.relativeTo(root).invariantSeparatorsPath }

    /** Null when the longest-matching module (a nested one) does not own the file as src/<set>/{kotlin,java}. */
    private fun owned(path: String, modules: List<Module>): Owned? {
        val module = moduleFor(path, modules) ?: return null
        val inModule = path.removePrefix(module.dir + "/").split('/')
        if (inModule.size < 4 || inModule[0] != "src" || inModule[2] !in SOURCE_DIRS) return null
        return Owned(path, module, inModule[1])
    }
}
