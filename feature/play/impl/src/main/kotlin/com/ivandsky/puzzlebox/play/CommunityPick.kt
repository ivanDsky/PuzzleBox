package com.ivandsky.puzzlebox.play

/**
 * The puzzle the Community two-pane shows on its right: a [Puzzle] opened by its share code (a New today row or a
 * friend's puzzle, shown as the friend puzzle page) or one of your [Published] puzzles (shown as its published page).
 * It is saved as text ([encoded]) so a rotation and process death keep it.
 */
internal sealed interface CommunityPick {
    val code: String

    data class Puzzle(override val code: String) : CommunityPick

    data class Published(override val code: String) : CommunityPick

    val encoded: String
        get() = (if (this is Published) PUBLISHED else PUZZLE) + code

    companion object {
        private const val PUZZLE = "puzzle:"
        private const val PUBLISHED = "published:"

        /** The pick [encoded] saved, or null for text that is not one. */
        fun parse(text: String): CommunityPick? = when {
            text.startsWith(PUZZLE) -> Puzzle(text.removePrefix(PUZZLE))
            text.startsWith(PUBLISHED) -> Published(text.removePrefix(PUBLISHED))
            else -> null
        }
    }
}
