package com.ivandsky.puzzlebox.game.replay

/** Which side of a compare is ahead. */
enum class Lead {
    Mine,
    Theirs,
    Level;

    companion object {
        /** Lead at a moment of a compare: fewer hints, then more correct pieces. */
        fun of(mine: ReplayFacts, theirs: ReplayFacts): Lead =
            mine.hints.compareTo(theirs.hints).leadOrNull()
                ?: theirs.correct.compareTo(mine.correct).leadOrNull()
                ?: Level

        /**
         * Lead once both sides ended: solved beats unsolved, then fewer hints, then more correct,
         * then the lower end time. Time only decides when the outcome ties, so it stays last.
         */
        fun ended(mine: ReplayFacts, mineEndMs: Long, theirs: ReplayFacts, theirsEndMs: Long): Lead =
            theirs.solved.compareTo(mine.solved).leadOrNull()
                ?: mine.hints.compareTo(theirs.hints).leadOrNull()
                ?: theirs.correct.compareTo(mine.correct).leadOrNull()
                ?: mineEndMs.compareTo(theirsEndMs).leadOrNull()
                ?: Level

        private fun Int.leadOrNull(): Lead? = when {
            this < 0 -> Mine
            this > 0 -> Theirs
            else -> null
        }
    }
}
