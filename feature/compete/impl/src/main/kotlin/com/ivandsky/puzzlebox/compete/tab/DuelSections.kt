package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.LocalMutedStatus
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind

/**
 * The Duels list's sections, in display order. Each is one card with a coloured header. [Waiting] holds both a match
 * waiting on them and an open invite nobody has accepted.
 */
internal enum class DuelSection(val label: String, val tag: String) {
    Invites("Invites", "duels.section.invites"),
    YourMove("Your move", "duels.section.yourMove"),
    ResultsReady("Results ready", "duels.section.resultsReady"),
    Waiting("Waiting", "duels.section.waiting"),
    Finished("Finished", "duels.section.finished"),
}

internal fun DuelRowKind.section(): DuelSection = when (this) {
    DuelRowKind.Invited -> DuelSection.Invites
    DuelRowKind.YourMove -> DuelSection.YourMove
    DuelRowKind.RevealReady -> DuelSection.ResultsReady
    DuelRowKind.Waiting, DuelRowKind.Unclaimed -> DuelSection.Waiting
    is DuelRowKind.Finished -> DuelSection.Finished
}

/** One non-empty section and its rows, in the order the list gave them. */
internal class SectionRows(val section: DuelSection, val rows: List<DuelRow>)

/** [rows] grouped into their sections, in section order, leaving out every section with no row. */
internal fun sectionRows(rows: List<DuelRow>): List<SectionRows> =
    DuelSection.entries.mapNotNull { section ->
        rows.filter { it.kind.section() == section }.takeIf { it.isNotEmpty() }?.let { SectionRows(section, it) }
    }

/** The two columns of the wide Duels tab: [left] and [right] hold whole sections, each in display order. */
internal class DuelColumns(val left: List<SectionRows>, val right: List<SectionRows>)

private const val HeaderEstimate = 48
private const val RowEstimate = 64
private const val InviteRowEstimate = 116
private const val SectionGapEstimate = 16

private fun SectionRows.estimatedHeight(): Int =
    HeaderEstimate + rows.sumOf { if (it.kind == DuelRowKind.Invited) InviteRowEstimate else RowEstimate }

/**
 * Deals [sections], in order, to the shorter of two columns by estimated height (a header 48 dp, a row 64, an invite
 * row 116, 16 dp between the sections of a column); a tie goes left. With one section the right column stays empty.
 */
internal fun dealColumns(sections: List<SectionRows>): DuelColumns {
    val left = mutableListOf<SectionRows>()
    val right = mutableListOf<SectionRows>()
    var leftHeight = 0
    var rightHeight = 0
    for (section in sections) {
        val height = section.estimatedHeight()
        if (leftHeight <= rightHeight) {
            leftHeight += (if (left.isEmpty()) 0 else SectionGapEstimate) + height
            left += section
        } else {
            rightHeight += (if (right.isEmpty()) 0 else SectionGapEstimate) + height
            right += section
        }
    }
    return DuelColumns(left, right)
}

/** How a finished duel ended for me. */
internal enum class DuelOutcome { Won, Lost, Drawn }

/** My outcome of a finished duel; null when its scores were not kept. */
internal fun DuelRowKind.Finished.outcome(): DuelOutcome? {
    val mine = myScore ?: return null
    val theirs = theirScore ?: return null
    return when {
        mine > theirs -> DuelOutcome.Won
        mine < theirs -> DuelOutcome.Lost
        else -> DuelOutcome.Drawn
    }
}

/** How many of [rows] I won, lost and drew; rows without kept scores count in none. */
internal data class DuelTally(val won: Int, val lost: Int, val drawn: Int) {
    val isEmpty: Boolean get() = won + lost + drawn == 0
}

internal fun tallyOf(rows: List<DuelRow>): DuelTally {
    val outcomes = rows.mapNotNull { (it.kind as? DuelRowKind.Finished)?.outcome() }
    return DuelTally(
        won = outcomes.count { it == DuelOutcome.Won },
        lost = outcomes.count { it == DuelOutcome.Lost },
        drawn = outcomes.count { it == DuelOutcome.Drawn },
    )
}

/** The colours and glyph an outcome wears on a tally chip and a score chip. */
internal class OutcomePaint(val fill: Color, val onFill: Color, val icon: ImageVector)

@Composable
internal fun DuelOutcome.paint(): OutcomePaint {
    val scheme = MaterialTheme.colorScheme
    val muted = LocalMutedStatus.current
    return when (this) {
        DuelOutcome.Won -> OutcomePaint(scheme.primary, scheme.onPrimary, PuzzleIcons.Trophy)
        DuelOutcome.Lost -> OutcomePaint(scheme.error, scheme.onError, PuzzleIcons.Close)
        DuelOutcome.Drawn -> OutcomePaint(muted.container, muted.content, PuzzleIcons.Equals)
    }
}

private class SectionPaint(val strong: Color, val onStrong: Color, val container: Color, val onContainer: Color)

@Composable
private fun DuelSection.paint(): SectionPaint {
    val scheme = MaterialTheme.colorScheme
    val muted = LocalMutedStatus.current
    return when (this) {
        DuelSection.Invites -> SectionPaint(scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer)
        DuelSection.YourMove -> SectionPaint(scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
        DuelSection.ResultsReady -> SectionPaint(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer)
        DuelSection.Waiting, DuelSection.Finished -> SectionPaint(muted.container, muted.content, muted.container, muted.content)
    }
}

@Composable
private fun DuelSection.icon(): ImageVector = when (this) {
    DuelSection.Invites -> PuzzleIcons.Mail
    DuelSection.YourMove -> PuzzleIcons.Play
    DuelSection.ResultsReady -> PuzzleIcons.Trophy
    DuelSection.Waiting -> PuzzleIcons.Hourglass
    DuelSection.Finished -> PuzzleIcons.Check
}

private val HeaderMinHeight = 48.dp
private val HeaderStart = 10.dp
private val HeaderGap = 10.dp
private val DiscSize = 30.dp
private val DiscIconSize = 18.dp
private val CountMinSize = 26.dp
private val CountPadding = 8.dp
private val TallyHeight = 28.dp
private val TallyIconSize = 15.dp
private val TallyGap = 6.dp
private val TallyChipGap = 4.dp
private val TallyStart = 7.dp
private val TallyEnd = 10.dp

/**
 * One section: a header (icon disc, uppercase label, and the row count, or for Finished the won / lost / draw tally),
 * then [rows] each drawn by [rowContent], hairlines between them. The status is said once, here; rows carry the name.
 */
@Composable
internal fun DuelSectionCard(
    section: SectionRows,
    modifier: Modifier = Modifier,
    rowContent: @Composable (DuelRow) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().testTag(section.section.tag),
        shape = AppShapes.large,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column {
            DuelSectionHeader(section)
            section.rows.forEachIndexed { index, row ->
                key(row.code.value) {
                    if (index > 0) HorizontalDivider(thickness = 1.dp, color = scheme.outlineVariant)
                    rowContent(row)
                }
            }
        }
    }
}

@Composable
private fun DuelSectionHeader(section: SectionRows) {
    val paint = section.section.paint()
    val tally = if (section.section == DuelSection.Finished) tallyOf(section.rows) else null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(paint.container)
            .heightIn(min = HeaderMinHeight)
            .padding(start = HeaderStart, end = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HeaderGap),
    ) {
        Box(Modifier.size(DiscSize).background(paint.strong, CircleShape), contentAlignment = Alignment.Center) {
            Icon(section.section.icon(), contentDescription = null, modifier = Modifier.size(DiscIconSize), tint = paint.onStrong)
        }
        Text(
            text = section.section.label.uppercase(),
            modifier = Modifier.weight(1f),
            color = paint.onContainer,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
            maxLines = 1,
        )
        if (tally == null || tally.isEmpty) CountBubble(section.rows.size) else TallyChips(tally)
    }
}

@Composable
private fun CountBubble(count: Int) {
    Box(
        modifier = Modifier
            .heightIn(min = CountMinSize)
            .widthIn(min = CountMinSize)
            .background(MaterialTheme.colorScheme.surface, PillShape)
            .padding(horizontal = CountPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge.tabular().copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun TallyChips(tally: DuelTally) {
    Row(horizontalArrangement = Arrangement.spacedBy(TallyGap), modifier = Modifier.testTag("duels.finished.tally")) {
        if (tally.won > 0) TallyChip(DuelOutcome.Won, tally.won)
        if (tally.lost > 0) TallyChip(DuelOutcome.Lost, tally.lost)
        if (tally.drawn > 0) TallyChip(DuelOutcome.Drawn, tally.drawn)
    }
}

@Composable
private fun TallyChip(outcome: DuelOutcome, count: Int) {
    val scheme = MaterialTheme.colorScheme
    val paint = outcome.paint()
    // The header is the muted colour, so a draw chip in that colour would vanish: it takes a raised surface instead.
    val fill = if (outcome == DuelOutcome.Drawn) scheme.surfaceContainerHigh else paint.fill
    val onFill = if (outcome == DuelOutcome.Drawn) scheme.onSurface else paint.onFill
    Row(
        modifier = Modifier
            .heightIn(min = TallyHeight)
            .background(fill, PillShape)
            .padding(start = TallyStart, end = TallyEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TallyChipGap),
    ) {
        Icon(paint.icon, contentDescription = null, modifier = Modifier.size(TallyIconSize), tint = onFill)
        Text(
            text = count.toString(),
            color = onFill,
            style = MaterialTheme.typography.labelLarge.tabular().copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
}
