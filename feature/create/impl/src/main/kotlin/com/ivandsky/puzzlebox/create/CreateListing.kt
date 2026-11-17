package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import java.time.Instant

/** The drafts still being made beside the published rows. */
internal data class Listed(val drafts: List<Draft>, val published: List<PublishedPuzzle>)

/**
 * The Custom list's state from its inputs, as of [now]: every stored draft with its [settled] chip, the Published
 * rows, the [removed] draft's Undo and the [newGames] the sheet offers.
 */
internal fun stateOf(
    listed: Listed,
    settled: Map<DraftStamp, Verdict?>,
    removed: Draft?,
    newGames: List<CreateUiState.NewGame>,
    now: Instant,
    games: GameRegistry,
    editors: GameEditorRegistry,
    plays: GamePlayRegistry,
): CreateUiState {
    val stored = listed.drafts
    return CreateUiState(
        loading = false,
        drafts = stored.map { rowOf(it, settled, now, games, editors, plays) },
        draftCount = stored.size,
        games = newGames,
        deleted = removed?.let { CreateUiState.Deleted(it.id, it.title) },
        published = listed.published.map { publishedRowOf(it, plays, now) },
    )
}
