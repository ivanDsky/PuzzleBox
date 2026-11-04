package com.ivandsky.puzzlebox.konsist.core

/** Index arithmetic over code text ([CommentStripper.code]), shared by the rules and the Gradle readers. */
object Scan {
    /**
     * Index after the bracket that closes the one at [open], or null when it never closes. [code] must have its
     * literal contents blanked ([CommentStripper.code]), so a bracket inside a string is never counted.
     */
    fun closing(code: String, open: Int): Int? {
        var depth = 0
        for (i in open until code.length) {
            when (code[i]) {
                '(', '{', '[' -> depth++
                ')', '}', ']' -> if (--depth == 0) return i + 1
            }
        }
        return null
    }

    /** Index of the first non-whitespace character at or after [from]; the text length when there is none. */
    fun skipSpace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }
}
