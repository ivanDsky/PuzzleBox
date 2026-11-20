package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.TwoColumnFrame
import com.ivandsky.puzzlebox.designsystem.TwoColumnMinWidth
import com.ivandsky.puzzlebox.designsystem.TwoColumnRightWidth
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.model.GameId

/**
 * A recap's whole screen for [state]: the story once the cards are ready, a quiet message while they are not.
 * [onPage] reports the page the story reaches; [onClose] leaves the recap. A window wide enough for two columns
 * ([TwoColumnMinWidth]) puts the story on the left and every fact as a list on the right. A [share] state adds the
 * Share page after the last card; [onShare] gets the image it captures. [onRetry] reads the recap again after an error.
 * [coverRise] holds the cover's words back while the story grows out of its tile.
 */
@Composable
internal fun RecapScreen(
    state: RecapUiState,
    periodLabel: String,
    gameName: (GameId) -> String,
    onPage: (Int) -> Unit,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    displayName: String? = null,
    share: RecapShareState? = null,
    onShare: (ImageBitmap, RecapShareContent) -> Unit = { _, _ -> },
    onRetry: () -> Unit = {},
    coverRise: CoverRise? = null,
) {
    val reduced = rememberReducedMotion()
    val fade = if (reduced) {
        tween<Float>(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear)
    } else {
        tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard)
    }
    // Keyed by the kind of state: the friends card arriving swaps the story's cards in place, with no fade.
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
        label = "recap.state",
        contentKey = { it::class },
    ) { shown ->
        RecapStateContent(shown, periodLabel, gameName, onPage, onWatchReplay, onClose, displayName, share, onShare, onRetry, coverRise)
    }
}

@Composable
private fun RecapStateContent(
    state: RecapUiState,
    periodLabel: String,
    gameName: (GameId) -> String,
    onPage: (Int) -> Unit,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    onClose: () -> Unit,
    displayName: String?,
    share: RecapShareState?,
    onShare: (ImageBitmap, RecapShareContent) -> Unit,
    onRetry: () -> Unit,
    coverRise: CoverRise?,
    modifier: Modifier = Modifier,
) {
    when (state) {
        RecapUiState.Loading -> RecapLoading(modifier)
        RecapUiState.Error -> RecapErrorFace(onRetry, onClose, modifier)
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
        is RecapUiState.Ready -> RiverScrubProvider {
            RecapReady(state, gameName, onPage, onWatchReplay, onClose, displayName, share, onShare, coverRise, modifier)
        }
    }
}

/** The skeleton where the story will be; on a tablet it fills the left pane and empty rows stand in for the facts. */
@Composable
private fun RecapLoading(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoColumnMinWidth) {
            val paneHeight = maxHeight
            TwoColumnFrame(
                paneHeight = paneHeight,
                tag = "recap.twoPane",
                contentMaxWidth = storyColumnsWidth(paneHeight),
                left = { column, height ->
                    Box(column.height(height).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                        RecapLoadingSkeleton(Modifier.fillMaxSize())
                    }
                },
                right = { RecapFactSkeleton(Modifier.height(paneHeight - Spacing.lg * 2)) },
            )
        } else {
            RecapLoadingSkeleton(Modifier.fillMaxSize())
        }
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
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    onClose: () -> Unit,
    displayName: String?,
    share: RecapShareState?,
    onShare: (ImageBitmap, RecapShareContent) -> Unit,
    coverRise: CoverRise?,
    modifier: Modifier = Modifier,
) {
    val pageCount = state.cards.size + if (share == null) 0 else 1
    // A fresh open starts on the cover; a saved page past the first card resumes on that card. A story grown out of its
    // tile arrives by the grow, so its cards are not dealt in as well.
    val deck = rememberSaveable(saver = RecapDeckState.Saver) {
        RecapDeckState(pageCount, state.initialPage, coverFirst = true).also { if (coverRise?.grown == true) it.dealt() }
    }.also { it.resize(pageCount) }
    LaunchedEffect(deck) { snapshotFlow { deck.page }.collect(onPage) }
    val periodLabel = state.title
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= TwoColumnMinWidth) {
            TwoColumnFrame(
                paneHeight = maxHeight,
                tag = "recap.twoPane",
                contentMaxWidth = storyColumnsWidth(maxHeight),
                left = { column, height ->
                    RecapStory(
                        cards = state.cards,
                        deck = deck,
                        period = state.period,
                        periodLabel = periodLabel,
                        gameName = gameName,
                        onWatchReplay = onWatchReplay,
                        onClose = onClose,
                        displayName = displayName,
                        share = share,
                        onShare = onShare,
                        twoPane = true,
                        coverRise = coverRise,
                        modifier = column
                            .height(height)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                },
                right = {
                    RecapFactList(
                        cards = state.cards,
                        current = deck.selectedFact,
                        gameName = gameName,
                        onSelect = deck::jumpTo,
                        onWatchReplay = onWatchReplay,
                    )
                },
            )
        } else {
            RecapStory(
                state.cards, deck, state.period, periodLabel, gameName, onWatchReplay, onClose,
                displayName = displayName, share = share, onShare = onShare, coverRise = coverRise,
            )
        }
    }
}

/**
 * The widest the story and the fact list get together in a pane [paneHeight] high: the story keeps the references'
 * shape at the full height, beside the gap and the fact list, and a wider window sets the rest aside as equal margins.
 */
private fun storyColumnsWidth(paneHeight: Dp): Dp =
    (paneHeight - Spacing.lg * 2) * StoryAspect + TwoColumnGap + TwoColumnRightWidth

/** The story pane's width over its height in the tablet references: 852 × 768dp at 1280 × 800. */
private const val StoryAspect = 852f / 768f

/** [TwoColumnFrame]'s gap between the columns. */
private val TwoColumnGap = Spacing.xl
