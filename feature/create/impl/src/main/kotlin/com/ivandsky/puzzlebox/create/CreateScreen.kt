package com.ivandsky.puzzlebox.create

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId

/** FAB height plus its bottom gap: the last row scrolls clear of the button. */
private val ListBottomInset = 56.dp + Spacing.lg * 2

/**
 * The Create root: the author's drafts, newest edit first, under the large "Create" title,
 * and the New puzzle button over the list's end corner. Past 8 drafts a search field
 * ([onQuery]) and game filter chips ([onFilter]) sit above the list. With no drafts at all
 * the [CreateEmptyState] lists the games inline instead, and the button hides. The button
 * opens the [NewPuzzleSheet] over [CreateUiState.games]; [onNew] starts a draft of the picked
 * game and size, [onOpen] opens one by id.
 *
 * Motion: rows **Fade in** / **Fade out** and slide to new places with a **Layout animation**
 * (MotionTokens medium, EasingStandard) as edits reorder them and the query narrows them;
 * none of it under reduced motion.
 */
@Composable
fun CreateScreen(
    state: CreateUiState,
    onNew: (gameId: GameId, variant: VariantId) -> Unit,
    onOpen: (draftId: String) -> Unit,
    onQuery: (String) -> Unit,
    onFilter: (GameId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LargeTopBar(title = "Create")
            when {
                state.loading -> Unit
                state.isEmpty -> CreateEmptyState(games = state.games, onStart = onNew)
                else -> DraftList(state, onOpen, onQuery, onFilter)
            }
        }
        if (!state.loading && !state.isEmpty) {
            NewPuzzleButton(
                onClick = { sheetOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.lg),
            )
        }
    }
    if (sheetOpen) {
        NewPuzzleSheet(
            games = state.games,
            onPick = { gameId, variant ->
                sheetOpen = false
                onNew(gameId, variant)
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun DraftList(
    state: CreateUiState,
    onOpen: (draftId: String) -> Unit,
    onQuery: (String) -> Unit,
    onFilter: (GameId?) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ListBottomInset),
    ) {
        if (state.searchVisible) searchItems(state, onQuery, onFilter)
        items(state.drafts, key = { it.id }) { row ->
            DraftRow(
                row = row,
                onOpen = { onOpen(row.id) },
                modifier = Modifier.animateItem(
                    fadeInSpec = if (reducedMotion) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
                    placementSpec = if (reducedMotion) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
                    fadeOutSpec = if (reducedMotion) null else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
                ),
            )
        }
        if (state.drafts.isEmpty()) {
            item(key = "no-match") {
                Text(
                    text = "No drafts match “${state.query.trim()}”.",
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xl)
                        .testTag("create.search.empty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The search field and the game filter chips: "All", then one per game with drafts. */
private fun LazyListScope.searchItems(state: CreateUiState, onQuery: (String) -> Unit, onFilter: (GameId?) -> Unit) {
    item(key = "search") {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                .testTag("create.search"),
            placeholder = { Text("Search drafts") },
            leadingIcon = { Icon(PuzzleIcons.Search, contentDescription = null) },
            trailingIcon = if (state.query.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = { onQuery("") }, modifier = Modifier.testTag("create.search.clear")) {
                        Icon(PuzzleIcons.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = PillShape,
        )
    }
    item(key = "filters") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "all") {
                FilterChip(
                    selected = state.filter == null,
                    onClick = { onFilter(null) },
                    label = { Text("All") },
                    modifier = Modifier.testTag("create.filter.all"),
                )
            }
            items(state.filters, key = { it.id.value }) { chip ->
                FilterChip(
                    selected = state.filter == chip.id,
                    onClick = { onFilter(chip.id) },
                    label = { Text("${chip.name} ${chip.count}") },
                    modifier = Modifier.testTag("create.filter.${chip.id.value}"),
                    leadingIcon = { GameGlyph(chip.brand, GameGlyphSize.Inline) },
                )
            }
        }
    }
}

@Composable
private fun NewPuzzleButton(onClick: () -> Unit, modifier: Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.testTag("create.new"),
        shape = AppShapes.medium,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(PuzzleIcons.Add, contentDescription = null) },
        text = { Text("New puzzle") },
    )
}

private fun previewRows(count: Int): List<CreateUiState.DraftRow> = (0 until count).map { index ->
    val game = PreviewGames[index % PreviewGames.size]
    CreateUiState.DraftRow(
        id = "d$index",
        gameId = game.id,
        brand = game.brand,
        title = "Untitled ${count - index}",
        meta = "${game.name} · ${game.defaultSize.label}",
        edited = "edited ${index + 1} h ago",
        verdict = if (index % 2 == 0) Verdict.Checking else Verdict.Undetermined,
    )
}

private val PreviewMany: CreateUiState = previewRows(12).let { rows ->
    CreateUiState(
        loading = false,
        drafts = rows,
        draftCount = rows.size,
        games = PreviewGames,
        searchVisible = true,
        filters = PreviewGames.map { CreateUiState.GameFilter(it.id, it.name, it.brand, 4) },
    )
}

@PuzzleBoxPreviews
@Composable
private fun CreateScreenPreview() {
    PuzzleBoxTheme {
        CreateScreen(
            state = CreateUiState(loading = false, drafts = previewRows(3), draftCount = 3, games = PreviewGames),
            onNew = { _, _ -> },
            onOpen = {},
            onQuery = {},
            onFilter = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun CreateScreenManyPreview() {
    PuzzleBoxTheme {
        CreateScreen(state = PreviewMany, onNew = { _, _ -> }, onOpen = {}, onQuery = {}, onFilter = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun CreateScreenEmptyPreview() {
    PuzzleBoxTheme {
        CreateScreen(
            state = CreateUiState(loading = false, games = PreviewGames),
            onNew = { _, _ -> },
            onOpen = {},
            onQuery = {},
            onFilter = {},
        )
    }
}
