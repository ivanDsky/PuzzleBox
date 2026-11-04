package com.ivandsky.puzzlebox.konsist.core

object BuildFileReader {
    data class Facts(val namespace: String?, val plugins: Set<String>)

    private val namespace = Regex("""\bnamespace\s*=\s*"([^"]+)"""")
    private val alias = Regex("""\balias\s*\(\s*libs\.plugins\.([\w.]+)\s*\)""")
    private val id = Regex("""\bid\s*\(\s*"([^"]+)"\s*\)""")
    private val kotlinPlugin = Regex("""\bkotlin\s*\(\s*"([^"]+)"\s*\)""")

    fun read(strippedBuildFile: String): Facts {
        val plugins = buildSet {
            alias.findAll(strippedBuildFile).forEach { add(it.groupValues[1]) }
            id.findAll(strippedBuildFile).forEach { add(it.groupValues[1]) }
            kotlinPlugin.findAll(strippedBuildFile).forEach { add("kotlin.${it.groupValues[1]}") }
        }
        return Facts(namespace.find(strippedBuildFile)?.groupValues?.get(1), plugins)
    }
}
