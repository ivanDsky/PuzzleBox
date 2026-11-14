package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.gameGlyph
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankWindowBoards
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window
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
 * The viewer's place on every board of [boards], read off what the hub already holds: Overall from its
 * [overall] state and each game from the shared window boards [answered] (null = none for this window yet),
 * the same mappings the boards draw. Scope never enters, so Friends keeps the global place (A6), and nothing here reads.
 * [brandOf] is each game's registered brand.
 */
internal fun boardPlaces(
    boards: List<RanksBoard>,
    registry: GameRegistry,
    brandOf: (GameId) -> GameBrand,
    overall: OverallState,
    answered: RankWindowBoards?,
    kind: Window,
    selfUid: String?,
): List<BoardPlace> = boards.map { board ->
    when (board) {
        RanksBoard.Overall -> BoardPlace(board, "Overall", overall.toDailyBoardState(selfUid).standing(), OverallBrand)
        is RanksBoard.Game -> BoardPlace(
            board,
            registry.displayName(board.id),
            answered.toGameBoardState(board.id, registry.rankOrderOf(board.id), kind, selfUid).standing(),
            brandOf(board.id),
        )
    }
}

private fun DailyBoardState.standing(): Standing = when (this) {
    DailyBoardState.Loading -> Standing.Pending
    is DailyBoardState.Ranked -> rows.firstOrNull { it.isSelf }?.let { if (it.beyondPage) Standing.Beyond else Standing.Place(it.rank) } ?: Standing.None
    else -> Standing.None
}
