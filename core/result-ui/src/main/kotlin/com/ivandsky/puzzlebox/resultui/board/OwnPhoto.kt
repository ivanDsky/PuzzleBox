package com.ivandsky.puzzlebox.resultui.board

/**
 * The signed-in player's account photo on every board row that is theirs. A leaderboard entry carries no photo, so
 * the account's is the one source: it replaces whatever the row had (a LinkedIn picture included, so the player looks
 * the same on every board) and a null [photo], an account without one, leaves the row as it is.
 */
fun DailyBoardRowModel.withOwnPhoto(photo: String?): DailyBoardRowModel =
    if (isSelf && photo != null) copy(photo = photo) else this

/** [withOwnPhoto] on the player's row among the lines; ghosts are other people's. */
fun List<DailyBoardLine>.withOwnPhoto(photo: String?): List<DailyBoardLine> =
    if (photo == null) this else map { if (it is DailyBoardLine.Real) it.copy(row = it.row.withOwnPhoto(photo)) else it }

/** [withOwnPhoto] on every row of a ranked board: its rows, the pinned row and the lines. Any other state has no rows. */
fun DailyBoardState.withOwnPhoto(photo: String?): DailyBoardState = when {
    photo == null || this !is DailyBoardState.Ranked -> this
    else -> copy(rows = rows.map { it.withOwnPhoto(photo) }, pinned = pinned?.withOwnPhoto(photo), lines = lines?.withOwnPhoto(photo))
}
