package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.linkedin.linkedInScore
import com.ivandsky.puzzlebox.gameui.bestScoreText

private val Tags = BoardRowTags("daily_board.ghost", "daily_board.ghost", "daily_board.ghost.detail")

/**
 * A LinkedIn connection's [row], drawn as every board row is: its [place] in the board's one ranking, or "—" while its
 * time is hidden. Instead of text it has a LinkedIn-blue frame and the LinkedIn dot on its photo. A hint-free time
 * reads in [order]'s voice; a hinted one shows the time alone, because a ghost's real hint count is unknown.
 * With [medals] a ghost at place 1 to 3 is a medal row too (see [MedalRow]).
 */
@Composable
fun GhostRow(row: GhostRowModel, place: Int?, order: RankOrder, modifier: Modifier = Modifier, medals: Boolean = false) {
    BoardRow(
        tags = Tags,
        name = row.name.ifEmpty { "Connection" },
        photo = row.photo,
        cells = row.cells(place, order, medals),
        modifier = modifier,
        scoreTime = row.timeMs?.let { time -> ScoreTime(time) { ms -> scoreAt(row, order, ms) } },
        connection = true,
        linkedIn = true,
        muted = row.timeMs == null,
    )
}

/** What this connection's row says at [place] on a board ranked by [order]: the cells that change when the board does. */
internal fun GhostRowModel.cells(place: Int?, order: RankOrder, medals: Boolean) = RowCells(
    place = place,
    detail = null,
    score = timeMs?.let { scoreAt(this, order, it) } ?: "time hidden",
    medalRow = medals,
)

private fun scoreAt(row: GhostRowModel, order: RankOrder, timeMs: Long): String =
    if (row.hintFree) bestScoreText(linkedInScore(timeMs, true), order) else formatDuration(timeMs)
