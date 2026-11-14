package com.ivandsky.puzzlebox.gameui.replay

import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.MoveKind
import com.ivandsky.puzzlebox.game.replay.ReplayFrame

/** What a replay player shows at one moment. Both sides of a compare share the position. */
class ReplayPlaybackState(
    val positionMs: Long,
    /** The end of the longer side. */
    val durationMs: Long,
    val playing: Boolean,
    val speed: Int,
    val skipIdle: Boolean,
    /** Move stamps for the scrubber, ascending: mine, or theirs when only theirs exists. */
    val ticksMs: LongArray,
    /** What each tick was, parallel to [ticksMs]. */
    val tickKinds: Array<MoveKind>,
    /** Null = that side has no replay. */
    val mineFrame: ReplayFrame?,
    val theirsFrame: ReplayFrame?,
    /** Null unless both sides have a replay. */
    val lead: Lead?,
    /** False hides every rule-violation mark on the boards. Both sides share it. */
    val showErrors: Boolean,
    /** The last change of position was a seek, and no frame of play has come since: the frame it landed on rests ([ReplayBoardOptions.animate]). */
    val seeked: Boolean = false,
)
