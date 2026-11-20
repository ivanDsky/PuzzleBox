package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RollingDigits
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * The Puzzles solved page: the period's total rolling up as an odometer over a sheet of bands, one per game in its own
 * colour and as tall as its share, with the hint-free stripe tilted between them when the period has a hint-free solve.
 * A wide pane puts the number on the left and the bands on the right, at no more than [SheetMaxWidth].
 */
@Composable
internal fun SolvedCardPage(
    card: RecapCard.Solved,
    periodLabel: String,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    val fade = rememberReducedFade()
    val ink = PaletteEntry.Solved.ink
    val striped = card.hintFree > 0
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade }
            .background(PaletteEntry.Solved.field)
            .clipToBounds()
            .testTag("recap.solved"),
    ) {
        val paneWidth = maxWidth
        CompositionLocalProvider(LocalContentColor provides ink) {
            val bands: @Composable (Modifier) -> Unit = {
                SolvedBands(card.perGame, gameName, if (striped) BandMinWithStripe else BandMin, it)
            }
            val stripe: @Composable () -> Unit = { SolvedStripe(card, stripeStartMs(card.perGame.size)) }
            if (paneWidth >= WideWidth) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxHeight().padding(top = Spacing.lg), verticalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.padding(horizontal = Spacing.xl)) { Total(card, periodLabel, striped, wide = true) }
                        if (striped) Box(Modifier.padding(bottom = Spacing.xl)) { stripe() }
                    }
                    bands(Modifier.fillMaxHeight().width(minOf(paneWidth * SheetShare, SheetMaxWidth)).padding(top = Spacing.lg))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(top = Spacing.lg)) {
                    Column(Modifier.padding(horizontal = Spacing.xl)) { Total(card, periodLabel, striped) }
                    if (striped) Box(Modifier.padding(top = Spacing.lg, bottom = Spacing.sm)) { stripe() }
                    bands(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

/** The period, the odometer total and its label, with the imported-days line of an imports-only year. */
@Composable
private fun Total(card: RecapCard.Solved, periodLabel: String, striped: Boolean, wide: Boolean = false) {
    Text(text = periodLabel, style = MaterialTheme.typography.titleMedium)
    RollingDigits(
        value = card.total.toLong(),
        modifier = Modifier.testTag("recap.solved.total"),
        style = MaterialTheme.typography.displayLarge.tabular().copy(
            fontSize = totalFontSize(card.total, striped, wide),
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        ),
        contentDescription = "${card.total} ${card.total.puzzles()} solved",
    )
    Text(
        text = "${card.total.puzzles()} solved",
        modifier = Modifier.clearAndSetSemantics { },
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    )
    card.importedDays?.let { days -> ImportedDaysLine(days) }
}

/** The odometer's size: the fewer the digits, the larger; smaller again with the stripe under it, and largest in a [wide] pane. */
internal fun totalFontSize(total: Int, striped: Boolean, wide: Boolean): TextUnit = when {
    wide -> when {
        total < 100 -> 300.sp
        total < 1000 -> 240.sp
        else -> 190.sp
    }
    total < 100 -> if (striped) 144.sp else 168.sp
    total < 1000 -> if (striped) 116.sp else 132.sp
    else -> if (striped) 92.sp else 104.sp
}

/** What a year built from imported results alone covers (A22), a quiet line under the total. */
@Composable
private fun ImportedDaysLine(days: Int) {
    Text(
        text = importedDaysLine(days),
        modifier = Modifier.padding(top = Spacing.sm).testTag("recap.solved.imported"),
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** Each game's solves in the period, in the app's game order, as one rounded block of the tablet list. */
@Composable
internal fun SolvedPerGameRows(
    perGame: List<RecapCard.Solved.PerGame>,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("recap.solved.list"),
    ) {
        perGame.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RowMinHeight)
                    .padding(horizontal = Spacing.lg)
                    .testTag("recap.solved.list.${row.game.value}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = gameName(row.game),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.solved.toString(),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The Puzzles solved fact in the tablet list: the title with the total, then each game's share. */
@Composable
internal fun SolvedCardFact(card: RecapCard.Solved, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = "Puzzles solved",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.total.toString(),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        card.importedDays?.let { days -> ImportedDaysLine(days) }
        SolvedPerGameRows(card.perGame, gameName)
        if (card.hintFree > 0) RecapFactRows(hintFreeRows(card), "recap.solved.fact.hintfree")
    }
}

/** The hint-free numbers as fact rows: the count, its share, the solves that used a hint and the longest run when there is one. */
private fun hintFreeRows(card: RecapCard.Solved): List<RecapFactRow> = buildList {
    add(RecapFactRow("recap.solved.fact.hintfree.count", "Solves without a hint", card.hintFree.toString()))
    add(RecapFactRow("recap.solved.fact.hintfree.share", "Share of your solves", "${share(card)}%"))
    if (card.total > card.hintFree) add(RecapFactRow("recap.solved.fact.hintfree.hinted", "Solved with a hint", (card.total - card.hintFree).toString()))
    card.longestHintFreeRun?.let { add(RecapFactRow("recap.solved.fact.hintfree.run", "Longest run", "$it in a row")) }
}

/** "puzzle" for one, "puzzles" otherwise: the page's label and the list row's. */
internal fun Int.puzzles(): String = if (this == 1) "puzzle" else "puzzles"

private val WideWidth = 600.dp
private const val SheetShare = 0.38f

/** The sheet stops growing at the reference tablet's width (0.38 of its 852dp pane), so a wider pane gives the extra to the number's side. */
private val SheetMaxWidth = 324.dp
private val RowMinHeight = 48.dp
