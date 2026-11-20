package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.model.GameId

/**
 * The recap as a story on [deck]: a cover ([RecapCover], made out for [displayName] when there is one) and then its
 * pages, dealt as a stack of cards ([RecapDeck]) under a progress strip with a segment per page (Fastest has one per
 * game), a replay button that plays the page's entrance
 * again (on the Share page, the end of the story, it replays the whole story from the cover), and a close button. The
 * top card's field fills the story behind them and the chrome takes its ink; [twoPane] insets the chrome a little more,
 * as a tablet's left pane does. The cover has no replay. Holding the deck hides that chrome, and the field with it, until
 * the touch lets go. With a [share] state the story ends on the Share page. System back steps back a card and, from the
 * cover or the first card, falls through to leave the recap. A [coverRise] holds the cover's words back while the story
 * grows out of its tile, then rises them in.
 */
@Composable
internal fun RecapStory(
    cards: List<RecapCard>,
    deck: RecapDeckState,
    period: RecapPeriod,
    periodLabel: String,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    displayName: String? = null,
    share: RecapShareState? = null,
    onShare: (ImageBitmap, RecapShareContent) -> Unit = { _, _ -> },
    twoPane: Boolean = false,
    coverRise: CoverRise? = null,
) {
    val palette = pagePalette(cards, deck.current)
    val reduced = rememberReducedMotion()
    val chrome by animateFloatAsState(
        targetValue = if (deck.holding) 0f else 1f,
        animationSpec = tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear),
        label = "recap.chrome",
    )
    val field by animateColorAsState(
        targetValue = palette.field,
        animationSpec = if (reduced) {
            tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear)
        } else {
            tween(RecapMotion.DeckStackPromote, easing = MotionTokens.EasingStandard)
        },
        label = "recap.field",
    )
    val turn by animateFloatAsState(
        targetValue = deck.entrance * ReplayTurn,
        animationSpec = if (reduced) snap() else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
        label = "recap.replay",
    )
    BackHandler(enabled = deck.canStepBack) { deck.back() }
    CompositionLocalProvider(LocalContentColor provides palette.ink) {
        Column(modifier.drawBehind { drawRect(field, alpha = chrome) }) {
            val inset = if (twoPane) Spacing.xs else 0.dp
            Column(
                Modifier
                    // Over the deck, so the cover's turning flare never covers the strip, ↻ or ✕.
                    .zIndex(1f)
                    .graphicsLayer { alpha = chrome }
                    .then(if (deck.holding) Modifier.clearAndSetSemantics {} else Modifier)
                    .height(DeckTop)
                    .padding(horizontal = Spacing.md + inset)
                    .padding(top = ChromeTop + inset),
            ) {
                RecapProgressStrip(count = deck.segmentCount, filled = deck.filledSegments)
                Row(
                    Modifier.align(Alignment.End).padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (!deck.onCover) {
                        if ((deck.current?.cardIndex ?: 0) < cards.size) {
                            ChromeButton(PuzzleIcons.Retry, "Replay card", "recap.replay", reduced, turn) { deck.restartEntrance() }
                        } else {
                            // The Share page is the end of the story: ↻ goes back to the start.
                            ChromeButton(PuzzleIcons.Retry, "Replay story", "recap.replayStory", reduced, onClick = deck::replay)
                        }
                    }
                    ChromeButton(PuzzleIcons.Close, "Close recap", "recap.close", reduced, onClick = onClose)
                }
            }
            RecapDeck(
                deck = deck,
                palette = { at -> pagePalette(cards, deck.pageAt(at)) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg)
                    .padding(bottom = Spacing.xl),
                yearFlick = { at -> deck.pageAt(at)?.let { cards.getOrNull(it.cardIndex) } is RecapCard.Month },
            ) { at ->
                val page = deck.pageAt(at)
                if (page == null) {
                    if (coverRise?.waiting != true) {
                        RecapCover(
                            title = coverTitle(period, periodLabel),
                            hook = coverHook(period, cards.filterIsInstance<RecapCard.Solved>().firstOrNull()),
                            name = displayName,
                            afterDeal = deck.dealing,
                            modifier = coverRise?.modifier ?: Modifier,
                        )
                    }
                } else if (page.cardIndex < cards.size) {
                    RecapCardPage(
                        cards[page.cardIndex], periodLabel, gameName, onWatchReplay, Modifier.fillMaxSize(),
                        monthPlace = monthPlaceOf(cards, page.cardIndex), displayName = displayName, onShare = { deck.forward() },
                        sub = page.sub,
                    )
                } else if (share != null) {
                    ShareCardPage(cards, gameName, share, onShare, Modifier.fillMaxSize())
                }
            }
            // A year deck keeps the foot's row on every card, so the deck does not resize between pages.
            if (cards.any { it is RecapCard.Month || it is RecapCard.YearFinale }) {
                Box(Modifier.fillMaxWidth().graphicsLayer { alpha = chrome }.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md).heightIn(min = FootHeight)) {
                    deckFootOf(cards, deck.current?.cardIndex ?: -1)?.let { DeckFoot(it) }
                }
            }
        }
    }
}

@Composable
private fun ChromeButton(
    icon: ImageVector,
    label: String,
    tag: String,
    reduced: Boolean,
    turn: Float = 0f,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val accent = LocalContentColor.current
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(ChromeButtonSize).pressScale(interaction, reduced).testTag(tag),
        colors = IconButtonDefaults.iconButtonColors(containerColor = accent.copy(alpha = ChromeTintAlpha), contentColor = accent),
        interactionSource = interaction,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.graphicsLayer { rotationZ = turn })
    }
}

private val FootHeight = 24.dp

/** Where the chrome starts and the card under it, as the story-shell references place them (prototype `#chrome`, `.b-card`). */
private val ChromeTop = 10.dp
private val DeckTop = 72.dp

/** Each replay turns the ↻ icon a further quarter turn back. */
private const val ReplayTurn = -90f

/** The tonal circle behind the chrome icons: the card's ink over its field, as the prototype's `.cb` draws it. */
private const val ChromeTintAlpha = 0.14f

private val ChromeButtonSize = 48.dp
