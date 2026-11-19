package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.ResultHeroChip

/** Below this pane height the page top-aligns and tightens, as the old result screen does. */
private val CompactHeight = 480.dp

/** The width at which the hero card and the side card sit side by side. */
private val TwoColumnWidth = 760.dp

/** The widest the content grows; a wider pane centres it. */
private val ContentMaxWidth = 1040.dp

/** The widest a single column grows, and the widest a Watch replay row grows; a wider phone-shaped pane centres it. */
val ResultColumnMaxWidth = 600.dp

/** The hero time's 96 sp step applies from a hero column this wide. */
private val HeroWideWidth = 520.dp

private const val HERO_WEIGHT = 1.1f
private const val SIDE_WEIGHT = 1f

/**
 * The daily Results page: the hero card and the side card, built
 * from one [facts] value so the cards only paint.
 *
 * Layout by measured width: under 760 dp one column, hero then side; from 760 dp two
 * columns weighted 1.1 to 1 with 32 dp page padding, the content capped at 1040 dp. The hero
 * time takes its 96 sp step once the hero column is 520 dp. In two columns both cards are
 * as tall as the taller one (the side column's moment card counts toward it); one column never stretches.
 * A stretched side card keeps its content at the top;
 * a stretched hero card keeps its header on the top padding and its best line, chips and actions on the bottom one, and
 * grows its time to fill the space between.
 * The cards report their content's own height and are given the larger of the two as a minimum
 * height, so a content change (the chart's scope switch) moves both. This replaces an intrinsic-height
 * row, which the chart's constraint-reading layout cannot answer. A pane under 480 dp tall
 * top-aligns and tightens; the page scrolls rather than squeezing the actions.
 *
 * The moment card (L1) sits under the hero card on one column, and first in the side column (above the streak card) on
 * two, so the celebration is on the first screen; the cards still line up to one height. Without moments (a duel leg) the cards are the same height.
 * [actions] is the hero's action row (the daily result passes [ResultHeroActions]); null draws none.
 * [chip] replaces the Today chip. A non-null [onRetryWrite] shows a failed-record-write notice above
 * the cards that retries through it. [onSignIn] is the rank row's sign-in, and the hero's sign-in line, when the player is signed out;
 * [signInNote] says under the rank row why the last try did not sign in.
 * [side], when given, is the side card's body in place of the streak, spread and rank, and makes the page two-pane on a wide screen
 * (a community puzzle's ranks list); below 760 dp it sits under the hero like the side card does.
 * [board], when given, is a block drawn below the cards (the duel leg result's card).
 * [replay], when given, is drawn last, below every other block in both layouts, so the actions stay on the first screen.
 * It spans the content width and is told the gap between the hero and side columns on two columns (null on one), so
 * it can line its parts up under the cards with [UnderResultColumns]; content for the hero column alone puts itself there.
 */
@Composable
fun ResultHeroScreen(
    facts: ResultHeroFacts,
    modifier: Modifier = Modifier,
    brand: GameBrand = GameBrand.Neutral,
    onSignIn: (() -> Unit)? = null,
    signInNote: String? = null,
    onRetryWrite: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    chip: ResultHeroChip? = null,
    board: (@Composable () -> Unit)? = null,
    side: (@Composable () -> Unit)? = null,
    replay: (@Composable (columnGap: Dp?) -> Unit)? = null,
) {
    val model = remember(facts, chip) { resultHeroModel(facts, chip = chip) }
    val hasSide = side != null || model.streak != null || model.spread != null || model.rank != null
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < CompactHeight
        val pagePadding = if (compact) Spacing.lg else Spacing.xxl
        val twoColumns = hasSide && maxWidth >= TwoColumnWidth
        val gap = if (compact) Spacing.md else Spacing.lg
        val contentWidth = minOf(maxWidth - pagePadding * 2, if (twoColumns) ContentMaxWidth else ResultColumnMaxWidth)
        val heroWidth = if (twoColumns) columnWidth(contentWidth, gap) else contentWidth
        val wide = heroWidth >= HeroWideWidth
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("result.hero.screen")
                .padding(
                    if (compact) PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm) else PaddingValues(pagePadding),
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (compact) Arrangement.Top else Arrangement.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = if (twoColumns) ContentMaxWidth else ResultColumnMaxWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                if (onRetryWrite != null) WriteFailedRow(onRetryWrite)
                if (twoColumns) {
                    val heroHeight = remember { mutableIntStateOf(0) }
                    val sideHeight = remember { mutableIntStateOf(0) }
                    val momentHeight = remember { mutableIntStateOf(0) }
                    val columnHeight = { maxOf(heroHeight.intValue, sideHeight.intValue + momentHeight.intValue) }
                    val heroMin = Modifier.minHeightPx(columnHeight)
                    val sideMin = Modifier.minHeightPx { columnHeight() - momentHeight.intValue }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                        ResultHeroCard(
                            model, brand, Modifier.weight(HERO_WEIGHT), wide, actions,
                            contentModifier = heroMin,
                            onContentHeight = { heroHeight.intValue = it },
                            onSignIn = onSignIn,
                        )
                        Column(Modifier.weight(SIDE_WEIGHT)) {
                            ResultMomentCard(
                                model.moments, brand, Modifier.onSizeChanged { momentHeight.intValue = it.height },
                                gap = gap, gapBelow = true,
                            )
                            ResultSideCard(
                                model, brand, onSignIn, Modifier.then(sideMin), wide, signInNote = signInNote,
                                contentModifier = Modifier.reportContentHeight { sideHeight.intValue = it },
                                content = side,
                            )
                        }
                    }
                } else {
                    Column {
                        ResultHeroCard(model, brand, Modifier, wide, actions, onSignIn = onSignIn)
                        ResultMomentCard(model.moments, brand, gap = gap)
                    }
                    ResultSideCard(model, brand, onSignIn, Modifier, wide, signInNote = signInNote, content = side)
                }
                board?.invoke()
                if (replay != null) Box { replay(if (twoColumns) gap else null) }
            }
        }
    }
}

/** The width of the hero column in a row of [HERO_WEIGHT] to [SIDE_WEIGHT] split by [gap] over [total]. */
private fun columnWidth(total: Dp, gap: Dp): Dp = (total - gap) * (HERO_WEIGHT / (HERO_WEIGHT + SIDE_WEIGHT))

/**
 * Lays [hero] and [side] out under the hero and side columns of a two-column Results page, [gap] apart as the cards are,
 * centred on each other. A null [side] leaves the side column empty.
 */
@Composable
internal fun UnderResultColumns(gap: Dp, hero: @Composable () -> Unit, side: (@Composable () -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(HERO_WEIGHT)) { hero() }
        Box(Modifier.weight(SIDE_WEIGHT)) { side?.invoke() }
    }
}

/**
 * Reports the height this content takes when nothing stretches it. The content is measured free of the
 * minimum height the card passes down (and held at the top of it), so the report never feeds back
 * into the minimum it helps set.
 */
private fun Modifier.reportContentHeight(onHeight: (Int) -> Unit): Modifier =
    wrapContentHeight(Alignment.Top).onSizeChanged { onHeight(it.height) }

/** At least [px] tall, read at layout time so a change relays out without recomposing. */
private fun Modifier.minHeightPx(px: () -> Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = px().coerceIn(constraints.minHeight, constraints.maxHeight)))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
