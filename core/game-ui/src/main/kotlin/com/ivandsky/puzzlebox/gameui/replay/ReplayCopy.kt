package com.ivandsky.puzzlebox.gameui.replay

/** The words more than one replay surface shows. A word only one feature shows stays in that feature. */
object ReplayCopy {
    const val TITLE = "Replay"
    const val WATCH = "Watch replay"
    const val NO_REPLAY = "No replay"
    const val UNAVAILABLE_IMPORTED = "Replay not available · imported from LinkedIn"
    const val UNAVAILABLE = "No replay for this solve"
    const val SKIP_IDLE = "Skip idle"
    const val PLAY = "Play"
    const val PAUSE = "Pause"
    const val PREVIOUS_MOVE = "Previous move"
    const val NEXT_MOVE = "Next move"
    const val SEEK = "Seek"
    const val BACK = "Back"
    const val SHOW_ERRORS = "Show errors"
    const val ON = "On"
    const val OFF = "Off"

    fun speed(x: Int): String = "$x×"

    fun speedDescription(x: Int): String = "Speed $x×"

    fun moves(count: Int): String = if (count == 1) "1 move" else "$count moves"

    /** The page's second line: where the game came from, then how long the replay is in moves. */
    fun subtitle(context: String, moves: Int): String = "$context · ${moves(moves)}"

    const val LEVEL = "Level"

    fun ahead(name: String, detail: String): String = "$name ahead · $detail"

    fun hints(count: Int): String = if (count == 1) "1 hint" else "$count hints"

    /** The flash on a hidden side's tab when its hint count rises by [count]. */
    fun hintGain(count: Int): String = if (count == 1) "+1 hint" else "+$count hints"

    fun piecesVs(leader: Int, other: Int, pieces: String): String = "$leader vs $other $pieces"

    fun hintsVs(leader: Int, other: Int): String = "${hints(leader)} vs ${hints(other)}"

    fun gaveUp(name: String, at: String): String = "$name gave up at $at"

    /** The side that still plays while the other has gone: where it stands. */
    fun playingAt(name: String, count: Int, pieces: String): String = "$name at $count $pieces"

    fun won(name: String, leaderTime: String, otherTime: String): String = "$name won · $leaderTime vs $otherTime"

    fun wonOverGaveUp(name: String, gaveUpLine: String): String = "$name won · $gaveUpLine"

    fun bothGaveUp(first: String, second: String): String = "$first · $second"
}
