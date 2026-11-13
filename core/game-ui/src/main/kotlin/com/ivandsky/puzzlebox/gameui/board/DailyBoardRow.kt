package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 */
@Composable
fun DailyBoardRow(
    row: DailyBoardRowModel,
    order: RankOrder,
    modifier: Modifier = Modifier,
    place: Int = row.rank,
    showPuzzleBoxRank: Boolean = false,
    medals: Boolean = false,
) {
    val value = row.scoreValue
    BoardRow(
        tags = if (row.isSelf) SelfTags else Tags,
        name = row.name.ifEmpty { "Player" },
        photo = row.photo,
        place = place,
        detail = if (showPuzzleBoxRank && !row.fromLinkedIn) "#${row.rank} on PuzzleBox" else boardRowValueDetail(value),
        score = boardRowValueText(value, order),
        modifier = modifier,
        scoreTime = value.scoreTime(order),
        self = row.isSelf,
        linkedIn = row.timeFromLinkedIn,
        mark = if (row.unofficial) UnofficialMark else null,
        beyondPage = row.beyondPage,
        medal = if (medals && !row.beyondPage && !row.unofficial) medalRowOf(place) else null,
        placeMedal = !row.unofficial,
    )
}
