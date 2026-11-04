package com.ivandsky.puzzlebox.konsist.core

object Brackets {
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
}
