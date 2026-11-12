package com.ivandsky.puzzlebox.play.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The one compare page of every surface: the duel reveal compares a leg's two replays, History compares a puzzle's
 * first win with its latest solve. Ids only, flat like [ReplayKey]: both sides are solves of the same puzzle, so one
 * [gameId] and one puzzle identity ([day] and [puzzleNumber], [puzzleId], or [code] and [legIndex], as [ReplayKey]
 * documents) serve both, and [mineKind] and [theirsKind] say which solve each side is (a [ReplayKey] kind). A timeline
 * or its bytes never ride a key, so the page survives process death. Build one with [of].
 *
 * The page labels the sides [myName] and [theirName] (the surface picks the words) and, with a [lead] (one of
 * [LEAD_MINE], [LEAD_THEIRS], [LEAD_LEVEL]), ends its lead line on that verdict.
 */
@Serializable
@SerialName("com.ivandsky.puzzlebox.play.api.ReplayCompare")
data class ReplayCompareKey(
    val gameId: String,
    val mineKind: String,
    val theirsKind: String,
    val myName: String,
    val theirName: String,
    val day: String? = null,
    val puzzleNumber: Int? = null,
    val puzzleId: String? = null,
    val code: String? = null,
    val legIndex: Int = 0,
    val lead: String? = null,
) : NavKey {

    /** The [ReplayKey] of the left side. */
    fun mine(): ReplayKey = side(mineKind)

    /** The [ReplayKey] of the right side. */
    fun theirs(): ReplayKey = side(theirsKind)

    private fun side(kind: String) = ReplayKey(gameId, kind, day, puzzleNumber, puzzleId, code, legIndex)

    companion object {
        const val LEAD_MINE = "mine"
        const val LEAD_THEIRS = "theirs"
        const val LEAD_LEVEL = "level"

        /** The compare of [mine] against [theirs], which must name the same puzzle and differ only in kind. */
        fun of(mine: ReplayKey, theirs: ReplayKey, myName: String, theirName: String, lead: String? = null): ReplayCompareKey {
            require(mine.copy(kind = theirs.kind) == theirs) { "a compare is of one puzzle's solves: $mine against $theirs" }
            return ReplayCompareKey(
                gameId = mine.gameId,
                mineKind = mine.kind,
                theirsKind = theirs.kind,
                myName = myName,
                theirName = theirName,
                day = mine.day,
                puzzleNumber = mine.puzzleNumber,
                puzzleId = mine.puzzleId,
                code = mine.code,
                legIndex = mine.legIndex,
                lead = lead,
            )
        }
    }
}
