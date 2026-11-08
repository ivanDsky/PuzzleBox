package com.ivandsky.puzzlebox.gameui.board

/**
 * A12: shown under the rows. [Posting], [Deferred] and [Rejected] tell how this player's
 * post stands ([toBoardNote]); the tablet play panel shows the same [text] for them.
 */
enum class DailyBoardNote(val text: String) {
    Posting("Posting your time…"),
    Deferred("Saved on this device — it'll post when you're online."),
    Rejected("Couldn't post this time."),
    ReplayFirstTimeStands("Replay — your first time stands"),
}
