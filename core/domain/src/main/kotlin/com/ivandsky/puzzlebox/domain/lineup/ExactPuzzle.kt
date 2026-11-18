package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchLeg

/**
 * One exact puzzle: an existing daily or custom leg reference, passed through. Create uses only [ref] and [authorUid];
 * [title], [author] and [source] are for the lineup row.
 */
data class ExactPuzzle(
    val ref: LegRef,
    val authorUid: String?,
    val title: String,
    val author: String?,
    val source: PuzzleSource,
) : LineupEntry {
    override val gameId get() = ref.gameId
}

@Suppress("UNUSED_PARAMETER")
internal suspend fun ExactPuzzle.resolve(context: LineupDrawContext): MatchLeg = MatchLeg(ref, authorUid)
