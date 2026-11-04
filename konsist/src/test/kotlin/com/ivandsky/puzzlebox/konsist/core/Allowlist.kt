package com.ivandsky.puzzlebox.konsist.core

class Allowlist(val ruleId: String, val entries: List<AllowlistEntry>, val problems: List<Finding>) {
    companion object {
        fun parse(ruleId: String, text: String): Allowlist {
            val entries = mutableListOf<AllowlistEntry>()
            val problems = mutableListOf<Finding>()
            val firstLineByPath = mutableMapOf<String, Int>()
            text.lines().forEachIndexed { index, rawLine ->
                val lineNo = index + 1
                val content = rawLine.replace(COMMENT, "").trim()
                if (content.isEmpty()) return@forEachIndexed
                val entry = when (val parsed = parseLine(content, lineNo)) {
                    is Parsed.Ok -> parsed.entry
                    is Parsed.Bad -> {
                        problems += Finding.Malformed(lineNo, content, parsed.reason)
                        return@forEachIndexed
                    }
                }
                val first = firstLineByPath[entry.path]
                if (first != null) {
                    problems += Finding.Duplicate(lineNo, first, entry.path)
                } else {
                    firstLineByPath[entry.path] = lineNo
                    entries += entry
                }
            }
            return Allowlist(ruleId, entries, problems)
        }

        fun load(ruleId: String): Allowlist {
            val stream = Allowlist::class.java.classLoader.getResourceAsStream("allowlists/$ruleId.txt")
                ?: return Allowlist(ruleId, emptyList(), emptyList())
            return parse(ruleId, stream.bufferedReader().use { it.readText() })
        }

        /** `#` opens a comment at line start or after whitespace; inside a path it is part of the path. */
        private val COMMENT = Regex("""(^|\s)#.*""")

        private sealed interface Parsed {
            data class Ok(val entry: AllowlistEntry) : Parsed
            data class Bad(val reason: String) : Parsed
        }

        private fun parseLine(content: String, lineNo: Int): Parsed {
            val tokens = content.split(Regex("\\s+"))
            if (tokens.size > 2) return Parsed.Bad("more than two tokens")
            val path = tokens[0]
            if (path.startsWith("/")) return Parsed.Bad("absolute path")
            if ('\\' in path) return Parsed.Bad("backslash separator")
            if (path.split('/').any { it == ".." }) return Parsed.Bad("'..' segment")
            val ceiling = if (tokens.size == 2) {
                tokens[1].toIntOrNull()?.takeIf { it >= 0 }
                    ?: return Parsed.Bad("ceiling is not a non-negative integer")
            } else {
                null
            }
            return Parsed.Ok(AllowlistEntry(path, ceiling, lineNo))
        }
    }
}
