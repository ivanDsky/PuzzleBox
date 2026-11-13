package com.ivandsky.puzzlebox.create

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.maxContentWidth
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.GameFilterChip
import com.ivandsky.puzzlebox.model.GameId

/** FAB height plus its bottom gap: the last row scrolls clear of the button. */
private val ListBottomInset = 56.dp + Spacing.lg * 2

/**
 * The shown tab's list under the Drafts / Published switch, **Cross-fading** on a switch
 * (DurationMedium; ReducedFadeDuration under reduced motion). A tab with nothing in it while
 * the other has items says so: "No drafts" or "Nothing published yet", unless the first cloud
 * restore is still running ([CreateUiState.restoring]), when the tab says they are coming back (A34).
 */
@Composable
internal fun CreateTabContent(
    state: CreateUiState,
    onOpen: (draftId: String) -> Unit,
    onOpenPublished: (code: String) -> Unit,
    onActions: (draftId: String) -> Unit,
    onQuery: (String) -> Unit,
    onFilter: (GameId?) -> Unit,
) {
    val reduced = rememberReducedMotion()
    Crossfade(
        targetState = state.tab,
        animationSpec = tween(if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium),
        label = "createTab",
    ) { tab ->
        when {
            tab == CreateUiState.Tab.Published && state.published.isEmpty() && state.restoring -> CreateRestoring()
            tab == CreateUiState.Tab.Published && state.published.isEmpty() -> EmptyState(
                title = "Nothing published yet",
                message = "A draft can be published once it has exactly one solution (Unique) and you’ve test-solved it.",
                modifier = Modifier.fillMaxSize().testTag("create.published.empty"),
            )
            tab == CreateUiState.Tab.Published -> PublishedList(state, onOpenPublished)
            state.draftCount == 0 && state.restoring -> CreateRestoring()
            state.draftCount == 0 -> EmptyState(
                title = "No drafts",
                message = "Everything you started is published. Tap New puzzle to start another.",
                modifier = Modifier.fillMaxSize().testTag("create.drafts.empty"),
            )
            else -> DraftList(state, onOpen, onActions, onQuery, onFilter)
        }
    }
}

@Composable
private fun PublishedList(state: CreateUiState, onOpen: (code: String) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    val listState = rememberLazyListState()
    OnTabReselect { listState.animateScrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().maxContentWidth().testTag("create.published.list"),
        contentPadding = PaddingValues(bottom = ListBottomInset),
    ) {
        items(state.published, key = { it.code.value }) { row ->
            PublishedRow(row = row, onOpen = { onOpen(row.code.value) }, modifier = rowMotion(reducedMotion))
        }
    }
}

@Composable
private fun DraftList(
    state: CreateUiState,
    onOpen: (draftId: String) -> Unit,
    onActions: (draftId: String) -> Unit,
    onQuery: (String) -> Unit,
    onFilter: (GameId?) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val listState = rememberLazyListState()
    OnTabReselect { listState.animateScrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().maxContentWidth().testTag("create.list"),
        contentPadding = PaddingValues(bottom = ListBottomInset),
    ) {
        if (state.searchVisible) searchItems(state, onQuery, onFilter)
        items(state.drafts, key = { it.id }) { row ->
            DraftRow(
                row = row,
                onOpen = { onOpen(row.id) },
                onActions = { onActions(row.id) },
                modifier = rowMotion(reducedMotion),
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
                GameFilterChip(
                    brand = chip.brand,
                    label = "${chip.name} ${chip.count}",
                    selected = state.filter == chip.id,
                    onClick = { onFilter(chip.id) },
                    modifier = Modifier.testTag("create.filter.${chip.id.value}"),
                )
            }
        }
    }
}

/** Rows **Fade in** / **Fade out** and slide to new places (MotionTokens medium, EasingStandard); none under reduced motion. */
private fun LazyItemScope.rowMotion(reduced: Boolean): Modifier = Modifier.animateItem(
    fadeInSpec = if (reduced) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
    placementSpec = if (reduced) null else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
    fadeOutSpec = if (reduced) null else tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
)
