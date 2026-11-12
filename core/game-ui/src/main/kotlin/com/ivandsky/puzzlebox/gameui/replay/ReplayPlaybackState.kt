package com.ivandsky.puzzlebox.gameui.replay

import com.ivandsky.puzzlebox.game.replay.Lead
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
    /** Null = that side has no replay. */
    val mineFrame: ReplayFrame?,
    val theirsFrame: ReplayFrame?,
    /** Null unless both sides have a replay. */
    val lead: Lead?,
)
