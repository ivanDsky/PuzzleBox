package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant

/** The search box and the game filter show only past this many drafts. */
internal const val SEARCH_THRESHOLD = 8

/** The drafts still being made beside the published rows. */
internal data class Listed(val drafts: List<Draft>, val published: List<PublishedPuzzle>)

/** What the user chose to look at: the search text, the game filter and the tab. */
internal data class View(val query: String, val filter: GameId?, val tab: CreateUiState.Tab)

/**
 * The Create root's state from its inputs, as of [now]: the drafts (searched and filtered only
 * past [SEARCH_THRESHOLD]) with their [settled] chips, the Published rows, the filter chips,
 * the [removed] draft's Undo and the [newGames] the sheet offers.
 */
internal fun stateOf(
    listed: Listed,
    settled: Map<DraftStamp, Verdict?>,
    view: View,
    removed: Draft?,
    newGames: List<CreateUiState.NewGame>,
    now: Instant,
    games: GameRegistry,
    editors: GameEditorRegistry,
    plays: GamePlayRegistry,
): CreateUiState {
    val stored = listed.drafts
    val text = view.query
    val game = view.filter
    val searchVisible = stored.size > SEARCH_THRESHOLD
    val needle = text.trim()
    val shownGame = game?.takeIf { searchVisible && stored.any { it.gameId == game } }
    val shown = if (!searchVisible) {
        stored
    } else {
        stored.filter { draft ->
            (shownGame == null || draft.gameId == shownGame) &&
                (needle.isEmpty() || draft.title.contains(needle, ignoreCase = true))
        }
    }
    return CreateUiState(
        loading = false,
        drafts = shown.map { rowOf(it, settled, now, games, editors, plays) },
        draftCount = stored.size,
        games = newGames,
        searchVisible = searchVisible,
        query = text,
        filters = if (searchVisible) filtersOf(stored, games, plays) else emptyList(),
        filter = shownGame,
        deleted = removed?.let { CreateUiState.Deleted(it.id, it.title) },
        tab = view.tab,
        published = listed.published.map { publishedRowOf(it, plays, now) },
    )
}
