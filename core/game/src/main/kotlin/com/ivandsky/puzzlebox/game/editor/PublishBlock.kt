package com.ivandsky.puzzlebox.game.editor

/**
 * One reason a draft cannot be published (spec A8): a stable [id] the UI maps to fix copy,
 * plus what it is about. [subject] is game-defined (the region id for [SPLIT_REGION]),
 * [count] a quantity (its piece count) and [cells] what "Select" highlights.
 */
data class PublishBlock(
    val id: String,
    val subject: Int = NO_SUBJECT,       // e.g. the region id for SPLIT_REGION
    val count: Int = 0,                   // e.g. the number of pieces
    val cells: Set<Int> = emptySet(),     // cells "Select" highlights
) {
    companion object {
        const val NO_SUBJECT = -1
        const val NOT_UNIQUE = "not-unique"           // emitted by 26 (domain)
        const val SPLIT_REGION = "split-region"       // emitted by 09 (Queens)
        const val NEEDS_GUESSING = "needs-guessing"   // emitted by 11 (Queens), M4 (Tango, Sudoku)
        const val EMPTY_TITLE = "empty-title"         // emitted by 26 (domain)
        const val TITLE_TOO_LONG = "title-too-long"   // emitted by 26 (domain)
        const val NOT_TESTED = "not-tested"           // emitted by 26 (domain)
        const val AUTHOR_TOO_LONG = "author-too-long" // emitted by the domain (ValidatePublishDetails)
        const val NOTE_TOO_LONG = "note-too-long"     // emitted by the domain (ValidatePublishDetails)
        // since M3: DAILY_CAP · since M4: BLOCKLISTED
    }
}
