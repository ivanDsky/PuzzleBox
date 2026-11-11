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
 */
@Composable
fun GhostRow(row: GhostRowModel, place: Int?, order: RankOrder, modifier: Modifier = Modifier) {
    BoardRow(
        tags = Tags,
        name = row.name.ifEmpty { "Connection" },
        photo = row.photo,
        place = place?.toString() ?: "—",
        detail = null,
        score = scoreText(row, order),
        modifier = modifier,
        connection = true,
        linkedIn = true,
        muted = row.timeMs == null,
    )
}

private fun scoreText(row: GhostRowModel, order: RankOrder): String {
    val timeMs = row.timeMs ?: return "time hidden"
    return if (row.hintFree) bestScoreText(linkedInScore(timeMs, true), order) else formatDuration(timeMs)
}
