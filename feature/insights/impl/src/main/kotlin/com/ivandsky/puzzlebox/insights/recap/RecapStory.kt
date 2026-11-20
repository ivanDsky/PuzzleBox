package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.model.GameId

/**
 * The recap as a story on [deck]: a cover ([RecapCover], made out for [displayName] when there is one) and then its
 * pages, dealt as a stack of cards ([RecapDeck]) under a progress strip, a replay button that plays the card's entrance
 * again (on the Share page, the end of the story, it replays the whole story from the cover), and a close button. The
 * cover has no replay. Holding the deck hides that chrome until the touch lets go. With a [share] state the story ends
 * on the Share page. System back steps back a card and, from the cover or the first card, falls through to leave the
 * recap.
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
) {
    val palette = recapPalette(cards, deck)
    val reduced = rememberReducedMotion()
    val chrome by animateFloatAsState(
        targetValue = if (deck.holding) 0f else 1f,
        animationSpec = tween(MotionTokens.ReducedFadeDuration, easing = MotionTokens.EasingLinear),
        label = "recap.chrome",
    )
    val turn by animateFloatAsState(
        targetValue = deck.entrance * ReplayTurn,
        animationSpec = if (reduced) snap() else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
        label = "recap.replay",
    )
    BackHandler(enabled = deck.canStepBack) { deck.back() }
    CompositionLocalProvider(LocalContentColor provides palette.ink) {
        Column(modifier) {
            Column(
                Modifier
                    .graphicsLayer { alpha = chrome }
                    .then(if (deck.holding) Modifier.clearAndSetSemantics {} else Modifier)
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.sm),
            ) {
                RecapProgressStrip(count = deck.segmentCount, filled = deck.filledSegments)
                Row(
                    Modifier.align(Alignment.End).padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (!deck.onCover) {
                        if (deck.page < cards.size) {
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
                palette = { at -> if (at == 0) PaletteEntry.Cover else pagePalette(cards, at - 1) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, top = Spacing.xs, end = Spacing.lg)
                    // Room under the top card for the cards peeking out below it.
                    .padding(bottom = Spacing.xl + RecapMotion.DeckStackStep * (RecapMotion.DeckStackVisible - 1)),
                yearFlick = { at -> cards.getOrNull(at - 1) is RecapCard.Month },
            ) { at ->
                val page = at - 1
                if (at == 0) {
                    RecapCover(
                        title = coverTitle(period, periodLabel),
                        hook = coverHook(period, cards.filterIsInstance<RecapCard.Solved>().firstOrNull()),
                        name = displayName,
                    )
                } else if (page < cards.size) {
                    RecapCardPage(
                        cards[page], periodLabel, gameName, onWatchReplay, Modifier.fillMaxSize(),
                        monthPlace = monthPlaceOf(cards, page), displayName = displayName, onShare = { deck.forward() },
                    )
                } else if (share != null) {
                    ShareCardPage(cards, share, onShare, Modifier.fillMaxSize())
                }
            }
            // A year deck keeps the foot's row on every card, so the deck does not resize between pages.
            if (cards.any { it is RecapCard.Month || it is RecapCard.YearFinale }) {
                Box(Modifier.fillMaxWidth().graphicsLayer { alpha = chrome }.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md).heightIn(min = FootHeight)) {
                    deckFootOf(cards, deck.position - 1)?.let { DeckFoot(it) }
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
    val accent = LocalPrimaryText.current
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

/** Each replay turns the ↻ icon a further quarter turn back. */
private const val ReplayTurn = -90f

/** The tonal circle behind the chrome icons: the icon's green over the screen, as the story-shell references measure. */
private const val ChromeTintAlpha = 0.14f

private val ChromeButtonSize = 48.dp
