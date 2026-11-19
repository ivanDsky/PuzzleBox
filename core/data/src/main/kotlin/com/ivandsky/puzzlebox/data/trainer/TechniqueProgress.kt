package com.ivandsky.puzzlebox.data.trainer

/**
 * What the trainer remembers about one technique for one game: its [mark], how many right
 * answers came in a row, and when the last answer was recorded. These are exactly the three
 * facts the synced document carries per technique, so the sync codec maps one to one. A
 * technique never answered is absent from the stored map and reads as [NEW].
 */
data class TechniqueProgress(
    /** Where the technique stands. Only ever rises. */
    val mark: Mark,
    /** Right answers in a row, without a wrong answer or a Show me between. */
    val inRow: Int,
    /** Epoch milliseconds of the most recent recorded answer; 0 when none. */
    val answeredAtMs: Long,
) {
    init {
        require(inRow >= 0 && answeredAtMs >= 0) { "inRow and answeredAtMs must not be negative" }
    }

    /** How far a technique has come. Declaration order is rank: a later entry is higher. */
    enum class Mark {
        /** Not yet answered. */
        New,

        /** At least one snippet answered. */
        Practicing,

        /** Five right in a row. Never dropped. */
        Mastered,
    }

    companion object {
        /** A technique nothing has been recorded for. */
        val NEW = TechniqueProgress(Mark.New, 0, 0L)
    }
}
