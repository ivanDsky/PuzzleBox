package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.RecapCardColors
import com.ivandsky.puzzlebox.designsystem.RollingDigits
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month of a yearly recap in the month's own colors: its name and solved count, rolling up on arrival, the month's
 * highlight with what else it did, its Moments stamped onto a board, its quickest solve and its best against your
 * average, twelve squares marking the month in the year, and, with a [place], "Month 3 of 12". One of these follows the
 * totals for each month of the year with a counted solve (A3). A short phone tightens the measures; a pane of 700dp or
 * wider puts the name, count and highlight on the left and the board and rows on the right.
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
    val colors = card.colors(LocalDarkTheme.current)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade }
            .background(colors.field)
            .testTag("recap.month.${card.month}"),
    ) {
        val width = maxWidth
        val roomy = width >= RoomyWidth
        val scale = MonthCardScale(roomy = roomy, compact = maxHeight < if (roomy) RoomyCompactHeight else CompactHeight)
        OnInk(colors.ink) {
            Column(
                Modifier.fillMaxSize().padding(if (roomy) RoomyPadding else PhonePadding),
                verticalArrangement = Arrangement.spacedBy(if (roomy) 16.dp else 12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Eyebrow(periodLabel)
                    if (place != null) Eyebrow("Month ${place.index} of ${place.count}", Modifier.testTag("recap.month.index"))
                }
                if (roomy) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RoomyGap)) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            MonthLead(card, colors, gameName, scale, maxWidth, Modifier.fillMaxSize(), Arrangement.SpaceBetween)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MonthStampBoard(card.moments, colors, gameName, scale.boardFloor, Modifier.weight(1f))
                            MonthPlateRows(card, colors, gameName, scale, rise = { riseIn(PlateRise + it) })
                        }
                    }
                } else {
                    MonthLead(card, colors, gameName, scale, width, Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp))
                    MonthStampBoard(card.moments, colors, gameName, scale.boardFloor, Modifier.weight(1f))
                    MonthPlateRows(card, colors, gameName, scale, rise = { riseIn(PlateRise + it) })
                }
                MonthSquares(card.month.monthValue)
            }
        }
    }
}

/** The small caps line at the top of a year card. */
@Composable
internal fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
}

/**
 * The month's name in kinetic letters, its rolling count over "puzzles solved", then its highlight and what else it did.
 * A roomy card sizes the name and count by its column's [width] and spreads them down the column ([arrangement]).
 */
@Composable
private fun MonthLead(
    card: RecapCard.Month,
    colors: RecapCardColors,
    gameName: (GameId) -> String,
    scale: MonthCardScale,
    width: Dp,
    modifier: Modifier,
    arrangement: Arrangement.Vertical,
) {
    val long = card.solved.toString().length > LongCount
    val nameSize = when {
        scale.roomy -> (width.value * 0.17f).coerceIn(56f, 96f).sp
        scale.compact -> 40.sp
        else -> 52.sp
    }
    val heroSize: TextUnit = when {
        scale.roomy && long -> (width.value * 0.24f).coerceIn(72f, 124f).sp
        scale.roomy -> (width.value * 0.30f).coerceIn(88f, 150f).sp
        else -> (if (scale.compact) 64f else 88f).let { if (long) it * LongHero else it }.sp
    }
    Column(modifier, verticalArrangement = arrangement) {
        RecapKineticTitle(card.monthName(), nameSize, Modifier.testTag("recap.month.name"))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RollingDigits(
                value = card.solved.toLong(),
                modifier = Modifier.testTag("recap.month.solved"),
                style = MaterialTheme.typography.displayLarge.tabular().copy(
                    fontSize = heroSize,
                    lineHeight = heroSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                contentDescription = "${card.solved} ${card.solved.puzzles()} solved",
            )
            Text(
                text = "${card.solved.puzzles()} solved",
                modifier = Modifier.clearAndSetSemantics { }.then(riseIn(3)),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = if (scale.roomy) 34.sp else 28.sp,
                    lineHeight = if (scale.roomy) 40.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthHighlightRow(card.lead, colors, gameName, scale, riseIn(4))
            card.also?.let { MonthAlsoLine(it, gameName, scale, riseIn(5)) }
        }
    }
}

/** "March": the month's own name; the year is the page's title. */
internal fun RecapCard.Month.monthName(locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

/** A pane this wide or wider lays the card out in two columns. */
private val RoomyWidth = 700.dp
private val RoomyGap = 40.dp

/** Under this height the card tightens its measures; a roomy card's threshold is a little lower. */
private val CompactHeight = 660.dp
private val RoomyCompactHeight = 640.dp
private val PhonePadding = PaddingValues(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 20.dp)
private val RoomyPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp)

/** A count longer than this many digits shows at [LongHero] of the hero size. */
private const val LongCount = 3
private const val LongHero = 0.82f

/** The plate rows rise after the headline (3), the highlight (4) and the also line (5). */
private const val PlateRise = 6
