package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.gameGlyph
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.model.GameId

/** Where the viewer stands on one board, as the title menu says it. */
internal sealed interface Standing {
    /** Ranked at [place]. */
    data class Place(val place: Int) : Standing

    /** On the board but past the rows it reads: "100+". */
    data object Beyond : Standing

    /** The board is still loading. */
    data object Pending : Standing

    /** No place to show: signed out, no solve in the window, or the board could not be read. */
    data object None : Standing

    /** `#12`, `100+`, `…` while loading, `—` for none. */
    fun label(): String = when (this) {
        is Place -> "#$place"
        Beyond -> "100+"
        Pending -> "…"
        None -> "—"
    }

    /** The label as a screen reader says it. */
    fun describe(): String = when (this) {
        is Place -> "place $place"
        Beyond -> "outside the top 100"
        Pending -> "loading"
        None -> "no place"
    }
}

/** One title-menu entry: a [board] by [name] and the viewer's [standing] on it. Its [brand] tints the hub while the board shows. */
internal data class BoardPlace(val board: RanksBoard, val name: String, val standing: Standing, val brand: GameBrand = GameBrand.Neutral)

/** Overall is no game: no hue, and a podium for a glyph. */
private val OverallBrand = GameBrand(AccentSlot.Neutral, gameGlyph(name = "Overall", stroke = "M4 20V14H9V20M9 20V8H15V20M15 20V11H20V20M3 20H21"))

/**
 * The viewer's place on every board of [boards], read off the [slices] the hub draws them from (same order), so the
 * menu and the hero say the place the board shows: among friends in Friends scope, a LinkedIn ghost ahead
 * counted, and nothing here reads. [brandOf] is each game's registered brand.
 */
internal fun boardPlaces(
    boards: List<RanksBoard>,
    registry: GameRegistry,
    brandOf: (GameId) -> GameBrand,
    slices: List<RanksBoardSlice>,
): List<BoardPlace> = boards.zip(slices) { board, slice ->
    when (board) {
        RanksBoard.Overall -> BoardPlace(board, "Overall", slice.state.standing(), OverallBrand)
        is RanksBoard.Game -> BoardPlace(board, registry.displayName(board.id), slice.state.standing(), brandOf(board.id))
    }
}

/** The viewer's official row's place; the unofficial row has none. With ghosts merged in, the place its line shows. */
private fun DailyBoardState.standing(): Standing = when (this) {
    DailyBoardState.Loading -> Standing.Pending
    is DailyBoardState.Ranked -> {
        val own = rows.firstOrNull { it.isSelf && !it.unofficial }
        when {
            own == null -> Standing.None
            own.beyondPage -> Standing.Beyond
            else -> Standing.Place(lines?.firstNotNullOfOrNull { line -> (line as? DailyBoardLine.Real)?.takeIf { it.row == own }?.place } ?: own.rank)
        }
    }
    else -> Standing.None
}
