package com.ivandsky.puzzlebox.konsist.core

/**
 * Reads the string values Gradle scripts pass to `include(…)`, `id(…)`, `kotlin(…)` and `namespace =`. A value is a
 * plain string literal or literals joined with `+`; anything else (a variable, a `$` template, a spread, a call) has a
 * value only Gradle can compute, so it fails loudly instead of being skipped.
 */
object GradleLiterals {
    /** The comma-separated values of an argument list (the text between the call's parentheses). */
    fun arguments(args: String, context: String): List<String> {
        val values = mutableListOf<String>()
        var i = Scan.skipSpace(args, 0)
        while (i < args.length) {
            val (value, end) = chain(args, i) ?: unreadable(context, args)
            values += value
            i = Scan.skipSpace(args, end)
            if (i < args.length) {
                if (args[i] != ',') unreadable(context, args)
                i = Scan.skipSpace(args, i + 1)
            }
        }
        return values
    }

    /** The value starting at [from] in [text]; whatever follows the last literal of the chain is not read. */
    fun value(text: String, from: Int, context: String): String =
        chain(text, Scan.skipSpace(text, from))?.first ?: unreadable(context, text.substring(from).lineSequence().first())

    /** `"a" + "b"` from [start] → ("ab", index after the last literal); null when [start] is not a literal. */
    private fun chain(s: String, start: Int): Pair<String, Int>? {
        val first = literal(s, start) ?: return null
        val value = StringBuilder(first.first)
        var end = first.second
        while (true) {
            val plus = Scan.skipSpace(s, end)
            if (s.getOrNull(plus) != '+') return value.toString() to end
            val next = literal(s, Scan.skipSpace(s, plus + 1)) ?: return null
            value.append(next.first)
            end = next.second
        }
    }

    /** A one-line `"…"` with no template or escape → (contents, index after the closing quote); null otherwise. */
    private fun literal(s: String, start: Int): Pair<String, Int>? {
        if (s.getOrNull(start) != '"' || s.startsWith("\"\"\"", start)) return null
        val close = s.indexOf('"', start + 1).takeIf { it > 0 } ?: return null
        val body = s.substring(start + 1, close)
        if (body.any { it == '$' || it == '\\' || it == '\n' }) return null
        return body to close + 1
    }

    private fun unreadable(context: String, text: String): Nothing =
        error("$context: cannot read `${text.trim()}`; use string literals, joined with + if needed")
}
