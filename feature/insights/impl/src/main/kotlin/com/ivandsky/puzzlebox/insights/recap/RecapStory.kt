package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.launch

/**
 * The recap as a story: one card to a full page under a progress strip and a close button. A tap on the right
 * two-thirds of the page or a swipe moves on, a tap on the left third or a swipe back moves back, and nothing moves by
 * itself (A7). System back steps back a card and, from the first card, falls through to leave the recap.
 */
@Composable
internal fun RecapStory(
    cards: List<RecapCard>,
    pagerState: PagerState,
    periodLabel: String,
    gameName: (GameId) -> String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val move: (Int) -> Unit = { delta ->
        val target = pagerState.currentPage + delta
        if (target in 0 until pagerState.pageCount) scope.launch { pagerState.showPage(target, reduced) }
    }
    BackHandler(enabled = pagerState.currentPage > 0) { move(-1) }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecapProgressStrip(count = cards.size, current = pagerState.currentPage, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.testTag("recap.close")) {
                Icon(PuzzleIcons.Close, contentDescription = "Close recap", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(pagerState, reduced) {
                    detectTapGestures { offset -> move(if (offset.x < size.width * BackZone) -1 else 1) }
                }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Next card") { move(1); true },
                        CustomAccessibilityAction("Previous card") { move(-1); true },
                    )
                }
                .testTag("recap.pager"),
        ) { page ->
            RecapCardPage(cards[page], periodLabel, gameName)
        }
    }
}

/** Moves to [page]: a slide, or a cut when the player asked for reduced motion. */
internal suspend fun PagerState.showPage(page: Int, reducedMotion: Boolean) {
    if (reducedMotion) scrollToPage(page) else animateScrollToPage(page)
}

/** The left share of the page a tap steps back from; the rest steps on. */
private const val BackZone = 1f / 3f
