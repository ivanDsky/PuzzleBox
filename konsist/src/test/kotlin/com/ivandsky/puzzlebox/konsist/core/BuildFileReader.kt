package com.ivandsky.puzzlebox.konsist.core

object BuildFileReader {
    data class Facts(val namespace: String?, val plugins: Set<String>)

    private const val CONTEXT = "build.gradle.kts"
    private val pluginsBlock = Regex("""\bplugins\s*\{""")
    private val pluginCall = Regex("""\b(alias|id|kotlin)\s*\(""")
    private val catalogAlias = Regex("""^\s*libs\.plugins\.([\w.]+)\s*$""")
    private val namespace = Regex("""\bnamespace\s*=""")

    /** Plugin ids come only from `plugins { }` blocks; a value that is not string literals fails loudly. */
    fun read(buildFile: String): Facts {
        val code = CommentStripper.code(buildFile)
        val stripped = CommentStripper.strip(buildFile)
        val plugins = buildSet {
            for (block in pluginsBlock.findAll(code)) {
                val open = block.range.last
                val close = Brackets.closing(code, open) ?: error("$CONTEXT: unclosed plugins {")
                for (call in pluginCall.findAll(code.substring(0, close), open)) {
                    val argsOpen = call.range.last
                    val argsClose = Brackets.closing(code, argsOpen) ?: error("$CONTEXT: unclosed ${call.value}")
                    val args = stripped.substring(argsOpen + 1, argsClose - 1)
                    when (call.groupValues[1]) {
                        "alias" -> add(catalogAlias.find(args)?.groupValues?.get(1) ?: error("$CONTEXT: cannot read alias(${args.trim()})"))
                        "id" -> addAll(GradleLiterals.arguments(args, "$CONTEXT id"))
                        else -> GradleLiterals.arguments(args, "$CONTEXT kotlin").forEach { add("kotlin.$it") }
                    }
                }
            }
        }
        val namespaceValue = namespace.find(code)?.let { GradleLiterals.value(stripped, it.range.last + 1, "$CONTEXT namespace") }
        return Facts(namespaceValue, plugins)
    }
}
