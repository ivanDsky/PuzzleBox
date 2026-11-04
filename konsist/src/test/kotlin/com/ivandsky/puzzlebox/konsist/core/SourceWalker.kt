package com.ivandsky.puzzlebox.konsist.core

import java.io.File

object SourceWalker {
    private val SOURCE_DIRS = listOf("kotlin", "java")
    private val EXCLUDED_ROOT_DIRS = setOf("konsist", "build-logic")
    private val EXCLUDED_ANYWHERE = setOf("build")
    private val PACKAGE_HEADER = Regex("""(?m)^[ \t]*package[ \t]+([^\s;]+)""")

    /** Kotlin files of every module's src/<set>/{kotlin,java}, sorted by path; each file has exactly one module. */
    fun walk(root: File, modules: List<Module>): List<SourceFile> =
        modules.flatMap { candidates(root, it) }
            .distinct()
            .filterNot(::isExcluded)
            .mapNotNull { path -> owned(root, path, modules) }
            .sortedBy { it.path }

    /** Whole-segment prefix, longest dir wins, so "games/zip-ui/…" never lands in "games/zip". */
    fun moduleFor(path: String, modules: List<Module>): Module? =
        modules.filter { path.startsWith(it.dir + "/") }.maxByOrNull { it.dir.length }

    /** Segment test on a root-relative path; a prefix test would also hit "builder/" or "konsistent/". */
    fun isExcluded(path: String): Boolean {
        val segments = path.split('/')
        return segments.first() in EXCLUDED_ROOT_DIRS || segments.dropLast(1).any { it in EXCLUDED_ANYWHERE }
    }

    private fun candidates(root: File, module: Module): List<String> =
        File(root, module.dir + "/src").listFiles { f -> f.isDirectory }.orEmpty().flatMap { setDir ->
            SOURCE_DIRS.flatMap { lang ->
                File(setDir, lang).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }
        }.map { it.relativeTo(root).invariantSeparatorsPath }

    /** Null when the longest-matching module (a nested one) does not own the file as src/<set>/{kotlin,java}. */
    private fun owned(root: File, path: String, modules: List<Module>): SourceFile? {
        val module = moduleFor(path, modules) ?: return null
        val inModule = path.removePrefix(module.dir + "/").split('/')
        if (inModule.size < 4 || inModule[0] != "src" || inModule[2] !in SOURCE_DIRS) return null
        val sourceSet = inModule[1]
        val text = File(root, path).readText()
        val stripped = CommentStripper.strip(text)
        val packageName = PACKAGE_HEADER.find(stripped)?.groupValues?.get(1).orEmpty()
        return SourceFile(path, module, sourceSet, SourceSetKind.of(sourceSet), packageName, text, stripped)
    }
}
