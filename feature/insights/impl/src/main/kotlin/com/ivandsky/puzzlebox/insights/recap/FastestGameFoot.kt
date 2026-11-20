package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import java.text.NumberFormat

/**
 * A Fastest page's foot: the day, the puzzle, how far under the average and the solves in [periodLabel], then the
 * replay pill. [wide], the cells share one row with the pill at its end; else they are a list, one hairline between
 * rows, over a full-width pill. A screen reader hears the cells as the day and puzzle; the page says the rest.
 */
@Composable
internal fun FastestGameFoot(
    row: RecapCard.Fastest.Row,
    gameName: String,
    periodLabel: String,
    slam: Slam,
    accent: Color,
    ink: Color,
    tight: Boolean,
    wide: Boolean,
    u: Float,
    onWatchReplay: () -> Unit,
) {
    val day = row.day.shortLabel()
    val meta = row.puzzleNumber?.let { "$day · Puzzle #$it" } ?: day
    val cells = listOfNotNull(
        "Set on" to day,
        row.puzzleNumber?.let { "Puzzle" to "#$it" },
        row.averageMs?.let { "Faster than your average" to formatDuration(it - row.timeMs) },
        "Solves in $periodLabel" to grouped(row.solves),
    )
    val label = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = (13 * u).sp, lineHeight = (16 * u).sp)
    val value = MaterialTheme.typography.headlineMedium.tabular().copy(fontWeight = FontWeight.ExtraBold)
    val facts = Modifier
        .graphicsLayer { rise(slam.meta.value) }
        .testTag("recap.fastest.meta")
        .clearAndSetSemantics { text = AnnotatedString(meta) }
    val pill: @Composable (Modifier) -> Unit = { m ->
        if (row.hasReplay) ReplayPill(gameName, row, slam, accent, ink, tight, u, onWatchReplay, m)
    }
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((24 * u).dp), verticalAlignment = Alignment.Bottom) {
            Row(facts.weight(1f), horizontalArrangement = spaceBetween((32 * u).dp)) {
                val cellValue = value.copy(fontSize = (26 * u).sp, lineHeight = (26 * u * 1.1f).sp)
                cells.forEach { (name, figure) ->
                    Column(Modifier.alignBy(LastBaseline), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        // A month's "Solves in October 2026" wraps rather than cuts off; the figures share one baseline.
                        if (!tight) Text(name, color = ink, style = label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(figure, color = ink, style = cellValue, maxLines = 1, softWrap = false)
                    }
                }
            }
            pill(Modifier)
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(facts.fillMaxWidth()) {
                val cellValue = value.copy(fontSize = (22 * u).sp, lineHeight = (22 * u * 1.1f).sp)
                cells.forEachIndexed { index, (name, figure) ->
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = ink.copy(alpha = HairlineAlpha))
                    Box(Modifier.fillMaxWidth().heightIn(min = (48 * u).dp).padding(vertical = (10 * u).dp), contentAlignment = Alignment.CenterStart) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((12 * u).dp)) {
                            Box(Modifier.weight(1f).alignByBaseline()) {
                                if (!tight) Text(name, color = ink, style = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(figure, Modifier.alignByBaseline(), ink, style = cellValue, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            pill(Modifier.fillMaxWidth())
        }
    }
}

/** [n] with its thousands grouped, as the player's locale writes it. */
internal fun grouped(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** Space-between with at least [gap] between, as the prototype's `.cells`. */
private fun spaceBetween(gap: androidx.compose.ui.unit.Dp) = object : Arrangement.Horizontal {
    override val spacing = gap
    override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) =
        with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
}

/** The hairline between the phone's rows: the ink at 24%. */
private const val HairlineAlpha = 0.24f
