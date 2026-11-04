package com.ivandsky.puzzlebox.konsist.core

object CommentStripper {
    /** Every comment char → ' ', newlines kept; result.length == text.length. Strings and chars untouched. */
    fun strip(text: String): String = scan(text, blankLiterals = false)

    /**
     * [strip], and the contents of string and char literals blanked as well; the quotes and `${…}` template
     * expressions stay. What is left is code only, for rules that match names (`Dispatchers.`, `android.`).
     */
    fun code(text: String): String = scan(text, blankLiterals = true)

    private fun scan(text: String, blankLiterals: Boolean): String {
        val out = text.toCharArray()
        Scanner(text, out, blankLiterals).code(0, inTemplate = false)
        return String(out)
    }

    private class Scanner(private val s: String, private val out: CharArray, private val blankLiterals: Boolean) {
        private fun blank(from: Int, to: Int) {
            for (k in from until to) if (out[k] != '\n') out[k] = ' '
        }

        private fun blankLiteral(from: Int, to: Int) {
            if (blankLiterals) blank(from, to)
        }

        /** Scans code from i; in a template expression stops after the closing '}'. Returns the next index. */
        fun code(start: Int, inTemplate: Boolean): Int {
            var i = start
            var depth = 0
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '/' && s.startsWith("//", i) -> {
                        var e = i
                        while (e < s.length && s[e] != '\n') e++
                        blank(i, e); i = e
                    }
                    c == '/' && s.startsWith("/*", i) -> {
                        var level = 0
                        var e = i
                        while (e < s.length) {
                            if (s.startsWith("/*", e)) { level++; e += 2 }
                            else if (s.startsWith("*/", e)) { level--; e += 2; if (level == 0) break }
                            else e++
                        }
                        blank(i, e); i = e
                    }
                    c == '"' && s.startsWith("\"\"\"", i) -> i = rawString(i + 3)
                    c == '"' -> i = string(i + 1)
                    c == '\'' -> i = charLiteral(i + 1)
                    inTemplate && c == '{' -> { depth++; i++ }
                    inTemplate && c == '}' -> { i++; if (depth == 0) return i; depth-- }
                    else -> i++
                }
            }
            return i
        }

        private fun string(start: Int): Int {
            var i = start
            var segment = start
            while (i < s.length) {
                when {
                    s[i] == '\\' -> i += 2
                    s[i] == '"' -> { blankLiteral(segment, i); return i + 1 }
                    s[i] == '\n' -> { blankLiteral(segment, i); return i }
                    s.startsWith("\${", i) -> {
                        blankLiteral(segment, i)
                        i = code(i + 2, inTemplate = true)
                        segment = i
                    }
                    else -> i++
                }
            }
            blankLiteral(segment, s.length)
            return s.length
        }

        private fun rawString(start: Int): Int {
            var i = start
            var segment = start
            while (i < s.length) {
                when {
                    s.startsWith("\"\"\"", i) -> {
                        while (i < s.length && s[i] == '"') i++
                        blankLiteral(segment, i - 3)
                        return i
                    }
                    s.startsWith("\${", i) -> {
                        blankLiteral(segment, i)
                        i = code(i + 2, inTemplate = true)
                        segment = i
                    }
                    else -> i++
                }
            }
            blankLiteral(segment, s.length)
            return s.length
        }

        private fun charLiteral(start: Int): Int {
            var i = start
            while (i < s.length) {
                when {
                    s[i] == '\\' -> i += 2
                    s[i] == '\'' -> { blankLiteral(start, i); return i + 1 }
                    s[i] == '\n' -> { blankLiteral(start, i); return i }
                    else -> i++
                }
            }
            blankLiteral(start, s.length)
            return s.length
        }
    }
}
