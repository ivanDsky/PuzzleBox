package com.ivandsky.puzzlebox.konsist.core

object SettingsReader {
    // "include" must be followed directly by "(" so includeBuild(...) is not a module.
    private val includeCall = Regex("""\binclude\s*\(""")

    /** Module paths of every `include(…)` in [settings]; an argument that is not string literals fails loudly. */
    fun includes(settings: String): List<String> {
        val code = CommentStripper.code(settings)
        val stripped = CommentStripper.strip(settings)
        return includeCall.findAll(code).flatMap { call ->
            val open = call.range.last
            val close = Scan.closing(code, open) ?: error("settings.gradle.kts: unclosed include(")
            GradleLiterals.arguments(stripped.substring(open + 1, close - 1), "settings.gradle.kts include")
        }.toList()
    }
}
