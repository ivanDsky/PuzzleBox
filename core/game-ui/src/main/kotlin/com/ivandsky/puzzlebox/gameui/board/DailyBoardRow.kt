package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.bestScoreText

private val Tags = BoardRowTags("daily_board.row", "daily_board.row", "daily_board.row.puzzlebox_rank")
private val SelfTags = BoardRowTags("daily_board.row.self", "daily_board.row", "daily_board.row.puzzlebox_rank")

/**
 * One ranked [row] at [place] in the board's one ranking, drawn as every board row is; the signed-in player's own row
 * stands out on the secondary container. [showPuzzleBoxRank] adds the row's own PuzzleBox rank under the name, which
 * differs from [place] once LinkedIn connections take places among the rows; a row added from LinkedIn has none.
 * A row whose time came from LinkedIn carries the LinkedIn dot on its photo.
 */
@Composable
fun DailyBoardRow(
    row: DailyBoardRowModel,
    order: RankOrder,
    modifier: Modifier = Modifier,
    place: Int = row.rank,
    showPuzzleBoxRank: Boolean = false,
) {
    BoardRow(
        tags = if (row.isSelf) SelfTags else Tags,
        name = row.name.ifEmpty { "Player" },
        photo = row.photo,
        place = "$place",
        detail = if (showPuzzleBoxRank && !row.fromLinkedIn) "#${row.rank} on PuzzleBox" else null,
        score = bestScoreText(BestScore(row.timeMs, row.hints), order),
        modifier = modifier,
        self = row.isSelf,
        linkedIn = row.timeFromLinkedIn,
    )
}
