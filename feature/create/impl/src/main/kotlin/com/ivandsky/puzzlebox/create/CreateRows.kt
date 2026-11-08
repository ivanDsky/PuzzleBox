package com.ivandsky.puzzlebox.create

import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import java.time.Instant

/** One Published-tab row: [row]'s game tile, its title, "Author 1:42" and its age as of [now]. */
internal fun publishedRowOf(row: PublishedPuzzle, plays: GamePlayRegistry, now: Instant) =
    CreateUiState.PublishedRow(
        code = row.code,
        gameId = row.gameId,
        brand = plays.brandOf(row.gameId),
        title = row.title,
        meta = if (row.unpublished) "Unpublished" else "Author ${formatDuration(row.authorTimeMs)}",
        published = ageLabel("published", row.createdAt, now),
        unpublished = row.unpublished,
        solves = row.solvesCount,
    )

/** One filter chip per game among [stored], with its draft count. */
internal fun filtersOf(
    stored: List<Draft>,
    games: GameRegistry,
    plays: GamePlayRegistry,
): List<CreateUiState.GameFilter> =
    stored.groupingBy { it.gameId }.eachCount().map { (id, count) ->
        CreateUiState.GameFilter(id, games.displayName(id), plays.brandOf(id), count)
    }

/** One Drafts-tab row: [draft]'s tile, "Game · size[ · imported]", its age as of [now] and its chip. */
internal fun rowOf(
    draft: Draft,
    settled: Map<DraftStamp, Verdict?>,
    now: Instant,
    games: GameRegistry,
    editors: GameEditorRegistry,
    plays: GamePlayRegistry,
): CreateUiState.DraftRow {
    val stamp = DraftStamp(draft.id, draft.updatedAt)
    val size = editors[draft.gameId]?.sizeLabel(draft.variant) ?: draft.variant.value
    val imported = if (draft.imported) " · imported" else ""
    return CreateUiState.DraftRow(
        id = draft.id,
        gameId = draft.gameId,
        brand = plays.brandOf(draft.gameId),
        title = draft.title,
        meta = "${games.displayName(draft.gameId)} · $size$imported",
        edited = ageLabel("edited", draft.updatedAt, now),
        verdict = if (stamp in settled) settled[stamp] else Verdict.Checking,
    )
}
