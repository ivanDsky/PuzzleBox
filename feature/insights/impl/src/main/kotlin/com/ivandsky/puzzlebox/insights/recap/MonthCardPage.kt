package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RollingDigits
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month of a yearly recap: its name over its solved count, rolling up on arrival, then the month's best against your
 * average (the game, its time and how far under), twelve squares marking the month in the year, and, with a [place], "Month 3 of 12" and what is
 * next. One of these follows the totals for each month of the year with a counted solve (A3). A wide pane puts the
 * name and count on the left and the rows on the right.
 */
@Composable
internal fun MonthCardPage(
    card: RecapCard.Month,
    periodLabel: String,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
    place: MonthPlace? = null,
) {
    val fade = rememberReducedFade()
    val palette = PaletteEntry.YearMonth
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade }
            .background(palette.field)
            .testTag("recap.month.${card.month}"),
    ) {
        val wide = maxWidth >= WideWidth
        OnInk(palette.ink) {
            Column(Modifier.fillMaxSize().padding(horizontal = Spacing.xl).padding(top = Spacing.xl, bottom = Spacing.lg)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Eyebrow(periodLabel)
                    if (place != null) Eyebrow("Month ${place.index} of ${place.count}", Modifier.testTag("recap.month.index"))
                }
                Spacer(Modifier.weight(1f))
                val rows: @Composable (Modifier) -> Unit = { MonthFactRows(card, gameName, it, container = palette.plate) }
                if (wide) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Count(card, wide = true) }
                        rows(Modifier.weight(1f).padding(start = Spacing.xl))
                    }
                } else {
                    Count(card, wide = false)
                }
                Spacer(Modifier.weight(1f))
                if (!wide) rows(Modifier.padding(bottom = Spacing.sm))
                MonthSquares(card.month.monthValue, if (wide) WideSquare else Square)
            }
        }
    }
}

/** The small caps line at the top of a year card. */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
}

/** The month's name in kinetic letters over its rolling count and the "puzzles solved" line. */
@Composable
private fun Count(card: RecapCard.Month, wide: Boolean) {
    RecapKineticTitle(card.monthName(), MonthNameSize, Modifier.testTag("recap.month.name"))
    RollingDigits(
        value = card.solved.toLong(),
        modifier = Modifier.testTag("recap.month.solved"),
        style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = HeroFontSize, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        delayMs = 0,
        durationMs = MotionTokens.ScoreCountUpDuration,
        contentDescription = "${card.solved} ${card.solved.puzzles()} solved",
    )
    Text(
        text = "${card.solved.puzzles()} solved",
        modifier = Modifier.clearAndSetSemantics { },
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = if (wide) 32.sp else 28.sp, fontWeight = FontWeight.Bold),
    )
}

/** The month's best against the player's own average: the game and its time, and how far under the average it was. Shared by the story and the tablet list. */
@Composable
internal fun MonthFactRows(
    card: RecapCard.Month,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tablet: Boolean = false,
) {
    val brand = LocalGameBrands.current(card.bestGame)
    val time = formatDuration(card.bestTimeMs)
    RecapFactRows(
        rows = listOf(
            if (tablet) {
                RecapFactRow("recap.month.best", "Best vs average", "$time · ${gameName(card.bestGame)}", game = brand)
            } else {
                RecapFactRow("recap.month.best", "Best against your average", "${gameName(card.bestGame)} $time", game = brand)
            },
            RecapFactRow("recap.month.under", underAverageLine(card.underAveragePercent), ""),
        ),
        tag = "recap.month.facts",
        modifier = modifier,
        container = container,
    )
}

/** "31% under your average"; a month that only matched it says so. */
internal fun underAverageLine(percent: Int): String = if (percent > 0) "$percent% under your average" else "Matched your average"

/** The month fact in the tablet list: its name with the solved count, then the quickest solve and its game. */
@Composable
internal fun MonthCardFact(card: RecapCard.Month, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = card.monthName(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.solved.toString(),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        MonthFactRows(card, gameName, tablet = true)
    }
}

/** "March": the month's own name; the year is the page's title. */
internal fun RecapCard.Month.monthName(locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

private val HeroFontSize = 104.sp
private val MonthNameSize = 56.sp
private val RowMinHeight = 48.dp
private val WideWidth = 600.dp
private val Square = 24.dp
private val WideSquare = 56.dp
