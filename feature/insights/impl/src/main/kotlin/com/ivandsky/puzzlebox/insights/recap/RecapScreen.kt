package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.TwoColumnFrame
import com.ivandsky.puzzlebox.designsystem.TwoColumnMinWidth
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.label
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.launch

/**
 * A recap's whole screen for [state]: the story once the cards are ready, a quiet message while they are not.
 * [onPage] reports the page the story reaches; [onClose] leaves the recap. A window wide enough for two columns
 * ([TwoColumnMinWidth]) puts the story on the left and every fact as a list on the right.
 */
@Composable
internal fun RecapScreen(
    state: RecapUiState,
    periodLabel: String,
    gameName: (GameId) -> String,
    onPage: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        RecapUiState.Loading -> Box(modifier.fillMaxSize())
        RecapUiState.Restoring -> RecapMessage(
            periodLabel = periodLabel,
            title = "Bringing back your history…",
            message = "Your recap will show up here in a moment.",
            onClose = onClose,
            tag = "recap.restoring",
            modifier = modifier,
        )
        RecapUiState.Empty -> RecapMessage(
            periodLabel = periodLabel,
            title = "Nothing to recap",
            message = "No puzzles were solved in this period.",
            onClose = onClose,
            tag = "recap.empty",
            modifier = modifier,
        )
        is RecapUiState.Ready -> RecapReady(state, gameName, onPage, onClose, modifier)
    }
}

@Composable
private fun RecapMessage(
    periodLabel: String,
    title: String,
    message: String,
    onClose: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        CompactTopBar(
            title = periodLabel,
            navigationIcon = {
                IconButton(onClick = onClose, modifier = Modifier.testTag("recap.close")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        EmptyState(title = title, message = message, modifier = Modifier.fillMaxSize().testTag(tag))
    }
}

@Composable
private fun RecapReady(
    state: RecapUiState.Ready,
    gameName: (GameId) -> String,
    onPage: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = state.initialPage) { state.cards.size }
    LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect(onPage) }
    val periodLabel = state.period.label()
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoColumnMinWidth) {
            val scope = rememberCoroutineScope()
            val reduced = rememberReducedMotion()
            TwoColumnFrame(
                paneHeight = maxHeight,
                tag = "recap.twoPane",
                left = { column, height ->
                    RecapStory(
                        cards = state.cards,
                        pagerState = pagerState,
                        periodLabel = periodLabel,
                        gameName = gameName,
                        onClose = onClose,
                        modifier = column
                            .height(height)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                },
                right = {
                    RecapFactList(
                        cards = state.cards,
                        current = pagerState.currentPage,
                        gameName = gameName,
                        onSelect = { page -> scope.launch { pagerState.showPage(page, reduced) } },
                    )
                },
            )
        } else {
            RecapStory(state.cards, pagerState, periodLabel, gameName, onClose)
        }
    }
}
