package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.replay.Timeline

/** What [LoadSolveReplay.readLeg] concluded: a timeline, a settled absence, or a failed load. */
sealed interface LegReplayLoad {
    data class Opened(val timeline: Timeline) : LegReplayLoad
    data object Absent : LegReplayLoad
    data object Unavailable : LegReplayLoad
}
