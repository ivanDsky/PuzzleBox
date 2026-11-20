package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * The tablet's right column: every fact of the story as a list. The fact on the story's page is scrolled into view and
 * drawn as its card: the card's field, its ink and a strong border in the theme's text colour, as in the story; the others are plain rows with
 * a swatch of their card's colour. A tap on a fact calls [onSelect] with its position so the story jumps to its page.
 */
@Composable
internal fun RecapFactList(
    cards: List<RecapCard>,
    current: Int,
    gameName: (GameId) -> String,
    onSelect: (Int) -> Unit,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("recap.facts"),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        cards.forEachIndexed { index, card ->
            val selected = index == current
            val palette = card.palette()
            val field = palette.field
            val ink = palette.ink
            val inView = remember { BringIntoViewRequester() }
            LaunchedEffect(selected) { if (selected) inView.bringIntoView() }
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(inView)
                    .semantics { this.selected = selected }
                    .testTag("recap.fact.$index"),
                shape = MaterialTheme.shapes.large,
                color = if (selected) field else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (selected) ink else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    width = if (selected) SelectedBorder else NoBorder,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                CompositionLocalProvider(LocalFactMarker provides FactMarkerStyle(field, ink, selected)) {
                    OnField(selected, ink) { RecapFact(card, gameName, onWatchReplay, Modifier.padding(Spacing.md)) }
                }
            }
        }
    }
}

/** The swatch and ring that lead a fact's title: the card's colour on a plain row, a hollow ring in ink on the selected one. */
internal class FactMarkerStyle(val field: Color, val ink: Color, val selected: Boolean)

private val LocalFactMarker = staticCompositionLocalOf<FactMarkerStyle?> { null }

/** The marker of the fact being drawn, before its title; nothing outside the fact list. */
@Composable
internal fun FactMarker() {
    val style = LocalFactMarker.current ?: return
    val ring = if (style.selected) style.ink else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .padding(end = Spacing.sm)
            .size(MarkerSize)
            .border(if (style.selected) SelectedRing else PlainRing, ring, CircleShape)
            .background(if (style.selected) Color.Transparent else style.field, CircleShape),
    )
}

/**
 * Draws a selected fact in the card's [ink]: the facts take their text, secondary text, values and hairlines from the
 * theme, so the selected one reads on the card's field with the row block that sits behind its rows left clear.
 */
@Composable
private fun OnField(selected: Boolean, ink: Color, content: @Composable () -> Unit) {
    if (!selected) return content()
    val colors = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = colors.copy(
            onSurface = ink,
            onSurfaceVariant = ink,
            primary = ink,
            outlineVariant = ink.copy(alpha = HairlineAlpha),
            surfaceContainerHigh = Color.Transparent,
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/** One card as a list fact: the one place a [RecapCard] becomes a list entry, exhaustive like [RecapCardPage]. */
@Composable
private fun RecapFact(
    card: RecapCard,
    gameName: (GameId) -> String,
    onWatchReplay: (RecapCard.Fastest.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is RecapCard.Solved -> SolvedCardFact(card, gameName, modifier)
        is RecapCard.Month -> MonthCardFact(card, gameName, modifier)
        is RecapCard.Fastest -> FastestCardFact(card, gameName, onWatchReplay, modifier)
        is RecapCard.Streak -> StreakCardFact(card, modifier)
        is RecapCard.Moments -> MomentsCardFact(card, gameName, modifier)
        is RecapCard.TotalTime -> TotalTimeCardFact(card, modifier)
        is RecapCard.Friends -> FriendsCardFact(card, gameName, modifier)
        is RecapCard.YearFinale -> YearFinaleCardFact(card, gameName, modifier)
    }
}

private val SelectedBorder = 3.dp
private val NoBorder = 1.dp
private val MarkerSize = 14.dp
private val SelectedRing = 2.dp
private val PlainRing = 1.dp
private const val HairlineAlpha = 0.3f
