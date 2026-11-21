package com.ivandsky.puzzlebox.insights.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.recap.RecapArtPlate
import com.ivandsky.puzzlebox.designsystem.recap.RecapTileArt
import com.ivandsky.puzzlebox.designsystem.skeleton
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod

/**
 * The Insights hub: a bar with a back arrow over a grid of cover tiles, the years and then the months that have a
 * recap, newest first; on a tablet the tiles select and a large cover beside them opens. [onOpen] opens one period's
 * story; [onBack] leaves the hub; [onRetry] reads again after a failed read. [onOpenStats] adds the "Stats for all
 * games" row above the tiles and opens the all-games stats page; null (the page is dark) leaves the row out.
 */
@Composable
internal fun InsightsHubScreen(
    state: InsightsHubUiState,
    onOpen: (RecapPeriod) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenStats: (() -> Unit)? = null,
    onRetry: () -> Unit = {},
) {
    val topBar: @Composable () -> Unit = {
        CompactTopBar(
            title = "Insights",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("insights.hub.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
    Box(modifier.fillMaxSize().testTag("insights.hub")) {
        if (state is InsightsHubUiState.Ready) {
            HubReady(state, onOpen, onOpenStats, topBar)
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            topBar()
            when (state) {
                InsightsHubUiState.Loading -> HubLoading()
                InsightsHubUiState.Restoring -> HubEmpty("Bringing back your history…", "Your solved days will show up here in a moment.", "insights.hub.restoring")
                InsightsHubUiState.Empty -> HubEmpty("No recaps yet", "Solve a puzzle and its month shows up here.", "insights.hub.empty")
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        title = "Couldn't load your recaps",
                        message = "Your solves are safe. Try again in a moment.",
                        onRetry = onRetry,
                        modifier = Modifier.testTag("insights.hub.error"),
                    )
                }
            }
        }
    }
}

/** No tiles yet ([title] and [message] say why): a grey, empty mosaic above the words. */
@Composable
private fun HubEmpty(title: String, message: String, tag: String) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.lg).padding(bottom = EmptyLift).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RecapArtPlate(
            art = RecapTileArt.Mosaic(emptyList(), empty = MaterialTheme.colorScheme.outline.copy(alpha = EmptyCellAlpha)),
            plate = MaterialTheme.colorScheme.surfaceVariant,
            stamp = false,
            size = EmptyMosaicSize,
        )
        EmptyState(title, message, Modifier.widthIn(max = EmptyWidth).offset(y = -EmptyLift))
    }
}

/** The grid's skeleton: a year's tile and three rows of month tiles, shimmering. */
@Composable
private fun HubLoading() {
    val shape = RoundedCornerShape(TileRadius)
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.lg).semantics { contentDescription = "Loading your recap" }.testTag("insights.hub.loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.fillMaxWidth().height(124.dp).skeleton(color, shape))
        repeat(3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                repeat(2) { Box(Modifier.weight(1f).height(176.dp).skeleton(color, shape)) }
            }
        }
    }
}

private val EmptyMosaicSize = 96.dp
private val EmptyWidth = 360.dp

/** The empty cells are the outline tone thinned out, so they read on the plate in light and in dark (`outlineVariant` sank into it in dark). */
private const val EmptyCellAlpha = 0.4f

/** The reference sits the words 16dp closer to the mosaic and the pair 8dp above center. */
private val EmptyLift = 16.dp
private val TileRadius = 28.dp
