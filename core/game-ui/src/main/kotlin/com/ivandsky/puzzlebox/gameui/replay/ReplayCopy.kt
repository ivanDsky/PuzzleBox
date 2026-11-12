package com.ivandsky.puzzlebox.gameui.replay

/** The words more than one replay surface shows. A word only one feature shows stays in that feature. */
object ReplayCopy {
    const val TITLE = "Replay"
    const val WATCH = "Watch replay"
    const val NO_REPLAY = "No replay"
    const val SKIP_IDLE = "Skip idle"
    const val PLAY = "Play"
    const val PAUSE = "Pause"
    const val PREVIOUS_MOVE = "Previous move"
    const val NEXT_MOVE = "Next move"
    const val SEEK = "Seek"
    const val BACK = "Back"

    fun speed(x: Int): String = "$x×"

    fun speedDescription(x: Int): String = "Speed $x×"

    fun moves(count: Int): String = if (count == 1) "1 move" else "$count moves"

    /** The page's second line: where the game came from, then how long the replay is in moves. */
    fun subtitle(context: String, moves: Int): String = "$context · ${moves(moves)}"
}
