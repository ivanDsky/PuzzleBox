package com.ivandsky.puzzlebox.konsist.core

object SettingsReader {
    // "include" must be followed directly by "(" so includeBuild(...) is not a module.
    private val includeCall = Regex("""\binclude\s*\(([^)]*)\)""")
    private val quoted = Regex(""""([^"]+)"""")

    fun includes(strippedSettings: String): List<String> =
        includeCall.findAll(strippedSettings)
            .flatMap { quoted.findAll(it.groupValues[1]).map { m -> m.groupValues[1] } }
            .toList()
}
