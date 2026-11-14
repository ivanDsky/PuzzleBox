package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.ivandsky.puzzlebox.game.RankOrder

private val Tags = BoardRowTags("daily_board.row", "daily_board.row", "daily_board.row.puzzlebox_rank")
private val SelfTags = BoardRowTags("daily_board.row.self", "daily_board.row", "daily_board.row.puzzlebox_rank")

private const val UnofficialMark = "unofficial"

/**
 * One ranked [row] at [place] in the board's one ranking, drawn as every board row is; the signed-in player's own row
 * stands out on the secondary container. [showPuzzleBoxRank] adds the row's own PuzzleBox rank under the name, which
 * differs from [place] once LinkedIn connections take places among the rows; a row added from LinkedIn has none.
 * A row whose time came from LinkedIn carries the LinkedIn dot on its photo.
 * The score column says the row's [DailyBoardRowModel.scoreValue] in [order]'s voice; a row [DailyBoardRowModel.beyondPage]
 * reads "100+" for its place and an [DailyBoardRowModel.unofficial] one carries the "unofficial" mark. With [medals] the
 * rows at places 1 to 3 are medal rows (see [MedalRow]); an unofficial row takes no place, so it wears no medal.
 * With [inlineMarks] the mark and the rank under the name are chips beside it instead.
 * When the board opted into row taps, another player's official row is a button that reports its [row].
 */
@Composable
internal fun DailyBoardRow(
    row: DailyBoardRowModel,
    order: RankOrder,
    modifier: Modifier = Modifier,
    place: Int = row.rank,
    showPuzzleBoxRank: Boolean = false,
    medals: Boolean = false,
    inlineMarks: Boolean = false,
) {
    val onClick = LocalBoardRowClick.current?.takeIf { row.isTappable() }
    BoardRow(
        tags = if (row.isSelf) SelfTags else Tags,
        name = row.name.ifEmpty { "Player" },
        photo = row.photo,
        cells = row.cells(place, order, showPuzzleBoxRank, medals),
        modifier = if (onClick == null) modifier else modifier.clickable(role = Role.Button) { onClick(row) },
        scoreTime = row.scoreValue.scoreTime(order),
        self = row.isSelf,
        linkedIn = row.timeFromLinkedIn,
        inlineMarks = inlineMarks,
    )
}

/** What this row says at [place] on a board ranked by [order]: the cells that change when the board does. */
internal fun DailyBoardRowModel.cells(place: Int, order: RankOrder, showPuzzleBoxRank: Boolean, medals: Boolean) = RowCells(
    place = place,
    detail = if (showPuzzleBoxRank && !fromLinkedIn) "#$rank on PuzzleBox" else boardRowValueDetail(scoreValue),
    score = boardRowValueText(scoreValue, order),
    mark = if (unofficial) UnofficialMark else null,
    beyondPage = beyondPage,
    medalRow = medals && !beyondPage && !unofficial,
    placeMedal = !unofficial,
)

// A row of someone else on the official board: not mine, not a device-only result, not my LinkedIn time.
private fun DailyBoardRowModel.isTappable() = !isSelf && !unofficial && !fromLinkedIn && playerId.isNotEmpty()
