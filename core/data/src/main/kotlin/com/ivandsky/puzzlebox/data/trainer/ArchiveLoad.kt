package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.game.Puzzle

/** What one [TrainerArchive.load] came to; every case names the number that was asked for. */
sealed interface ArchiveLoad {

    val number: Int

    /** [puzzle] is on the device and the walker confirmed the technique, so a round can be cut from it. */
    data class Loaded(override val number: Int, val puzzle: Puzzle) : ArchiveLoad

    /** The puzzle loaded but the walker does not find the technique in it; it is skipped for the rest of the session. */
    data class Mismatch(override val number: Int) : ArchiveLoad

    /** No puzzle came, for [reason]; nothing on the device changed and the player's progress is untouched. */
    data class CantLoad(override val number: Int, val reason: Reason) : ArchiveLoad {

        enum class Reason {
            /** The call budget was used up or LinkedIn is throttling: retrying in a moment can work. */
            Busy,

            /** No connection or the feed failed: retrying can work. */
            Offline,

            /** The source has no such puzzle, no way to number days, or the cached copy no longer decodes: retrying cannot help. */
            Gone,

            /** The puzzle arrived but failed the engine's trust gate: retrying cannot help. */
            Untrusted,
        }
    }
}
