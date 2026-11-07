package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId

/** What [SharedOpen.verify] concluded about a received share payload. */
sealed interface SharedOpenResult {
    /** [puzzle] carries the solution derived on this device; [difficulty] is Verdict.Unique.difficulty. */
    data class Verified(val puzzle: Puzzle, val variant: VariantId, val difficulty: Difficulty?) : SharedOpenResult

    /** shareFormatVersion, or the payload's own version, is above what this build knows (A24). */
    data class NeedsUpdate(val found: Int, val maxKnown: Int) : SharedOpenResult

    /** The editor lacks EditorShareCodec or DraftExport. */
    data object NotShareable : SharedOpenResult

    /** Malformed payload, or any verdict but Unique (Multiple, None, Incomplete, Undetermined). */
    data object FailedCheck : SharedOpenResult
}
