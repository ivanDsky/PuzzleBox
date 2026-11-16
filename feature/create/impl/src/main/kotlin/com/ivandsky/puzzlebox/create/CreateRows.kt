package com.ivandsky.puzzlebox.create

import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import java.time.Instant

/**
 * One Published-tab row: [row]'s game tile, its title, "Author 1:42" (nothing for a row restored from a legacy doc, whose
 * author time is unknown) and its age as of [now].
 */
internal fun publishedRowOf(row: PublishedPuzzle, plays: GamePlayRegistry, now: Instant) =
    CreateUiState.PublishedRow(
        code = row.code,
        gameId = row.gameId,
        brand = plays.brandOf(row.gameId),
        title = row.title,
        meta = when {
            row.unpublished -> "Unpublished"
            row.authorTimeMs > 0 -> "Author ${formatDuration(row.authorTimeMs)}"
            else -> ""
        },
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

/** One Drafts-tab row: [draft]'s tile, "Game · size", its age as of [now] and its chip. */
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
    return CreateUiState.DraftRow(
        id = draft.id,
        gameId = draft.gameId,
        brand = plays.brandOf(draft.gameId),
        title = draft.title,
        meta = "${games.displayName(draft.gameId)} · $size",
        edited = ageLabel("edited", draft.updatedAt, now),
        verdict = if (stamp in settled) settled[stamp] else Verdict.Checking,
        imported = draft.imported,
    )
}

/**
 * The draft [rows] as list items, for the Create tab's Drafts list and the Custom list alike: a tap reports the row's
 * id to [onOpen], its ⋮ or long press to [onActions]. Keys carry a `draft:` prefix, so they stay unique in a list
 * that also holds published and friends' rows.
 */
internal fun LazyListScope.draftRows(
    rows: List<CreateUiState.DraftRow>,
    reducedMotion: Boolean,
    onOpen: (draftId: String) -> Unit,
    onActions: (draftId: String) -> Unit,
) {
    items(rows, key = { "draft:${it.id}" }, contentType = { "draft" }) { row ->
        DraftRow(
            row = row,
            onOpen = { onOpen(row.id) },
            onActions = { onActions(row.id) },
            modifier = rowMotion(reducedMotion),
        )
    }
}

/** The published [rows] as list items, for both lists too: a tap reports the row's share code to [onOpen]. Keys carry a `published:` prefix. */
internal fun LazyListScope.publishedRows(
    rows: List<CreateUiState.PublishedRow>,
    reducedMotion: Boolean,
    onOpen: (code: String) -> Unit,
) {
    items(rows, key = { "published:${it.code.value}" }, contentType = { "published" }) { row ->
        PublishedRow(row = row, onOpen = { onOpen(row.code.value) }, modifier = rowMotion(reducedMotion))
    }
}

/** Rows **Fade in** / **Fade out** and slide to new places (MotionTokens medium, EasingStandard); none under reduced motion. */
private fun LazyItemScope.rowMotion(reduced: Boolean): Modifier = Modifier.animateItem(
    fadeInSpec = if (reduced) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
    placementSpec = if (reduced) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
    fadeOutSpec = if (reduced) null else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
)
