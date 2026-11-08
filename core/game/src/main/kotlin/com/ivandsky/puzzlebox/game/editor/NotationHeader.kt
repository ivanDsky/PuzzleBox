package com.ivandsky.puzzlebox.game.editor

/** The notation header line `puzzlebox <game> v<n>` (spec A23), the format's own version stamp. */
data class NotationHeader(val gameId: String, val version: Int) {
    /** `puzzlebox <gameId> v<version>` */
    val line: String get() = "$PREFIX $gameId v$version"

    companion object {
        const val PREFIX: String = "puzzlebox"

        /** `puzzlebox <id> v<n>` with no leading zero and single spaces. */
        private val LINE = Regex("$PREFIX (\\S+) v([1-9]\\d*)")

        /** The first non-blank, non-`#` line of [text] when it reads `puzzlebox <id> v<n>` with n ≥ 1; else null. */
        fun find(text: String): NotationHeader? {
            val first = text.lineSequence()
                .firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("#") }
                ?.trim() ?: return null
            val match = LINE.matchEntire(first) ?: return null
            return NotationHeader(match.groupValues[1], match.groupValues[2].toInt())
        }
    }
}
