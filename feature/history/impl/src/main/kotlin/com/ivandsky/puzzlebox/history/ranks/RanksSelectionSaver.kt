package com.ivandsky.puzzlebox.history.ranks

import androidx.lifecycle.SavedStateHandle
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

internal const val BOARD_KEY = "ranks.board"
internal const val WINDOW_KEY = "ranks.window"
internal const val SCOPE_KEY = "ranks.scope"
internal const val DAY_KEY = "ranks.day"
internal const val SHOWN_DAY_KEY = "ranks.shownDay"
internal const val EXPANDED_KEY = "ranks.expanded"

private const val OVERALL = "overall"
private const val GAME_PREFIX = "game:"

/**
 * The [SavedStateHandle] codec for [RanksSelection] (A8): every pick as a plain string or boolean, so the
 * handle's bundle takes it. A null day is stored as absent and restores as null, which keeps the
 * midnight rollover.
 */
internal class RanksSelectionSaver(private val handle: SavedStateHandle) {

    fun save(selection: RanksSelection) {
        handle[BOARD_KEY] = when (val board = selection.board) {
            RanksBoard.Overall -> OVERALL
            is RanksBoard.Game -> GAME_PREFIX + board.id.value
        }
        handle[WINDOW_KEY] = selection.window.name
        handle[SCOPE_KEY] = selection.scope.name
        handle[DAY_KEY] = selection.day?.toString()
        handle[SHOWN_DAY_KEY] = selection.shownDay?.toString()
        handle[EXPANDED_KEY] = selection.calendarExpanded
    }

    /**
     * What the handle holds, or [fallback] when it holds nothing, a board the hub no longer lists (a
     * game removed since), or a value that no longer parses.
     */
    fun restore(fallback: RanksSelection, boards: List<RanksBoard>): RanksSelection {
        val saved = handle.get<String>(BOARD_KEY) ?: return fallback
        return runCatching {
            RanksSelection(
                board = boardOf(saved).also { require(it in boards) },
                window = RanksSelection.Window.valueOf(checkNotNull(handle.get<String>(WINDOW_KEY))),
                scope = RankScope.valueOf(checkNotNull(handle.get<String>(SCOPE_KEY))),
                day = handle.get<String>(DAY_KEY)?.let(LocalDate::parse),
                calendarExpanded = handle.get<Boolean>(EXPANDED_KEY) ?: false,
                shownDay = handle.get<String>(SHOWN_DAY_KEY)?.let(LocalDate::parse),
            )
        }.getOrDefault(fallback)
    }

    private fun boardOf(saved: String): RanksBoard = when {
        saved == OVERALL -> RanksBoard.Overall
        saved.startsWith(GAME_PREFIX) -> RanksBoard.Game(GameId(saved.removePrefix(GAME_PREFIX)))
        else -> error("unknown board $saved")
    }
}
