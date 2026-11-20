package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.SwipeMotion
import com.ivandsky.puzzlebox.designsystem.detectSwipe
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.swipeStep
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.model.GameId

/**
 * The recap as a story on [deck]: one card to a full page, painted in its card's colours, under a progress strip and a
 * close button. A tap on the right two-thirds of the page or a swipe moves on, a tap on the left third or a swipe back
 * moves back, and nothing moves by itself (A7). With a [share] state the story ends on the Share page. System back
 * steps back a card and, from the first card, falls through to leave the recap.
 */
@Composable
internal fun RecapStory(
    cards: List<RecapCard>,
    deck: RecapDeckState,
    periodLabel: String,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    share: RecapShareState? = null,
    onShare: (ImageBitmap, RecapShareContent) -> Unit = { _, _ -> },
) {
    val palette = recapPalette(cards, deck)
    BackHandler(enabled = deck.canStepBack) { deck.back() }
    CompositionLocalProvider(LocalContentColor provides palette.ink) {
        Column(modifier.background(palette.field)) {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecapProgressStrip(
                    count = deck.segmentCount,
                    filled = deck.filledSegments,
                    card = palette,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.testTag("recap.close")) {
                    Icon(PuzzleIcons.Close, contentDescription = "Close recap", tint = palette.ink)
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(deck) {
                        detectTapGestures { offset -> if (offset.x < size.width * BackZone) deck.back() else deck.forward() }
                    }
                    // After the tap detector so it sees a drag first and consumes it; a tap never reaches the slop.
                    .pointerInput(deck) {
                        val fling = SwipeMotion.FlingVelocity.toPx()
                        detectSwipe(onDrag = {}, onEnd = { dragPx, velocityPx ->
                            when (swipeStep(dragPx, velocityPx, size.width.toFloat(), fling)) {
                                1 -> deck.forward()
                                -1 -> deck.back()
                            }
                        })
                    }
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Next card") { deck.forward(); true },
                            CustomAccessibilityAction("Previous card") { deck.back(); true },
                        )
                    }
                    .testTag("recap.pager"),
            ) {
                if (deck.page < cards.size) {
                    RecapCardPage(cards[deck.page], periodLabel, gameName, onWatchReplay, Modifier.fillMaxSize())
                } else if (share != null) {
                    ShareCardPage(cards, share, onShare, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** The left share of the page a tap steps back from; the rest steps on. */
private const val BackZone = 1f / 3f
