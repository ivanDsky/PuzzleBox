package com.ivandsky.puzzlebox.gameui.board

/**
 * A12: shown under the rows. [Posting], [Deferred] and [Rejected] tell how this player's
 * post stands ([toBoardNote]); [PostedOutsideTop] says a Posted time stands beyond the
 * loaded rows (RG9); the tablet play panel shows the same [text] for the post notes.
 */
enum class DailyBoardNote(val text: String) {
    Posting("Posting your time…"),
    Deferred("Saved on this device — it'll post when you're online."),
    Rejected("Couldn't post this time."),

    /** RG9: the "100" is the backend board cap; its constant lives outside game-ui's reach, so the number rides this copy alone. */
    PostedOutsideTop("Your time is posted — outside the top 100."),
    ReplayFirstTimeStands("Replay — your first time stands"),
}
