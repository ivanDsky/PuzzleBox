package com.ivandsky.puzzlebox.domain

/** What one read of a leg's replay concluded: [Absent] is a settled no, [Unavailable] a read that failed and a retry may answer. */
sealed interface LegReplayRead {
    data class Found(val source: ReplaySource) : LegReplayRead
    data object Absent : LegReplayRead
    data object Unavailable : LegReplayRead
}
