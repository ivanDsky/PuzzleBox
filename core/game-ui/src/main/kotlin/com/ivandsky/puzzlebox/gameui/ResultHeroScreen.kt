package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Below this pane height the page top-aligns and tightens, as the old result screen does. */
private val CompactHeight = 480.dp

/** The width at which the hero card and the side card sit side by side. */
private val TwoColumnWidth = 760.dp

/** The widest the content grows; a wider pane centres it. */
private val ContentMaxWidth = 1040.dp

/** The widest a single column grows; a wider phone-shaped pane centres it. */
private val SingleColumnMaxWidth = 600.dp

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
 * time takes its 96 sp step once the hero column is 520 dp. A pane under 480 dp tall
 * top-aligns and tightens; the page scrolls rather than squeezing the actions.
 *
 * [actions] is the hero's action row (the daily result passes [ResultHeroActions]); null draws none.
 * [chip] replaces the Today chip. A non-null [onRetryWrite] shows a failed-record-write notice above
 * the cards that retries through it. [onSignIn] is the rank row's sign-in when the player is signed out.
 * [board], when given, is a block drawn below the cards (the duel leg result's card).
 * [replay], when given, is drawn last, below every other block in both layouts, so the actions stay on the first screen.
 */
@Composable
fun ResultHeroScreen(
    facts: ResultHeroFacts,
    modifier: Modifier = Modifier,
    brand: GameBrand = GameBrand.Neutral,
    onSignIn: (() -> Unit)? = null,
    onRetryWrite: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    chip: ResultHeroChip? = null,
    board: (@Composable () -> Unit)? = null,
    replay: (@Composable () -> Unit)? = null,
) {
    val model = remember(facts, chip) { resultHeroModel(facts, chip = chip) }
    val hasSide = model.streak != null || model.spread != null || model.rank != null
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < CompactHeight
        val pagePadding = if (compact) Spacing.lg else Spacing.xxl
        val twoColumns = hasSide && maxWidth >= TwoColumnWidth
        val gap = if (compact) Spacing.md else Spacing.lg
        val contentWidth = minOf(maxWidth - pagePadding * 2, if (twoColumns) ContentMaxWidth else SingleColumnMaxWidth)
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
                modifier = Modifier.widthIn(max = if (twoColumns) ContentMaxWidth else SingleColumnMaxWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                if (onRetryWrite != null) WriteFailedRow(onRetryWrite)
                if (twoColumns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                        ResultHeroCard(model, brand, Modifier.weight(HERO_WEIGHT), wide, actions)
                        ResultSideCard(model, brand, onSignIn, Modifier.weight(SIDE_WEIGHT), wide)
                    }
                } else {
                    ResultHeroCard(model, brand, Modifier, wide, actions)
                    ResultSideCard(model, brand, onSignIn, Modifier, wide)
                }
                board?.invoke()
                replay?.invoke()
            }
        }
    }
}

/** The width of the hero column in a row of [HERO_WEIGHT] to [SIDE_WEIGHT] split by [gap] over [total]. */
private fun columnWidth(total: Dp, gap: Dp): Dp = (total - gap) * (HERO_WEIGHT / (HERO_WEIGHT + SIDE_WEIGHT))
