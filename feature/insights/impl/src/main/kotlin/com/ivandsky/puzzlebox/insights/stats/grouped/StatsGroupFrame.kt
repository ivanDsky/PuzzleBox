package com.ivandsky.puzzlebox.insights.stats.grouped

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.insights.stats.LocalStatsFlat
import com.ivandsky.puzzlebox.insights.stats.RecordsSection
import com.ivandsky.puzzlebox.insights.stats.StatsSectionSlot
import com.ivandsky.puzzlebox.model.GameId

/**
 * One group's body under its sticky header, drawn flat (no cards, see [LocalStatsFlat]): the [StatsGroupBlock]'s main
 * block, then its side block, stacked; on a pane wide enough ([TwoUpMinWidth]) side by side, main to side at 1.3 : 1,
 * and a block on its own keeps the main column's width, so the charts of every group are as wide. The side block's Records
 * are the group's part of them, headed "Records · all time".
 */
@Composable
internal fun StatsGroupFrame(
    block: StatsGroupBlock,
    gameName: (GameId) -> String,
    onOpenSolve: (SolveRef) -> Unit,
    singleGame: Boolean,
    modifier: Modifier = Modifier,
) {
    val main: (@Composable (Modifier) -> Unit)? = block.main?.let { section -> { m -> Block(block.group, section, gameName, onOpenSolve, singleGame, m) } }
    val side: (@Composable (Modifier) -> Unit)? = block.side?.let { section -> { m -> Block(block.group, section, gameName, onOpenSolve, singleGame, m) } }
    CompositionLocalProvider(LocalStatsFlat provides true) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val inset = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            if (maxWidth >= TwoUpMinWidth) {
                Row(inset, horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalAlignment = Alignment.Top) {
                    // A lone block takes the main column and leaves the side one empty.
                    Box(Modifier.weight(MAIN_WEIGHT)) { (main ?: side)?.invoke(Modifier) }
                    Box(Modifier.weight(SIDE_WEIGHT)) { if (main != null) side?.invoke(Modifier) }
                }
            } else {
                Column(inset, verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                    main?.invoke(Modifier)
                    side?.invoke(Modifier)
                }
            }
        }
    }
}

@Composable
private fun Block(
    group: StatsGroup,
    section: StatsSection,
    gameName: (GameId) -> String,
    onOpenSolve: (SolveRef) -> Unit,
    singleGame: Boolean,
    modifier: Modifier,
) {
    if (section is StatsSection.Records) {
        RecordsSection(
            section.state,
            gameName,
            onOpenSolve,
            modifier,
            title = AllTimeTitle,
            tag = "stats.group.records.${group.name.lowercase()}",
            titleStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        StatsSectionSlot(section, gameName, onOpenSolve, singleGame, modifier)
    }
}

/** "Records · all time": the label semibold, "· all time" regular. */
private val AllTimeTitle = buildAnnotatedString {
    append("Records")
    withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(" · all time") }
}

/** The narrowest pane that lays a group out two-up; the tablet's stats pane is well over it, a phone well under. */
private val TwoUpMinWidth = 640.dp
private const val MAIN_WEIGHT = 1.3f
private const val SIDE_WEIGHT = 1f
