package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.model.GameId

/**
 * Where a [StatsSection] is drawn: one line per section kind. A later section adds its composable's file and its line
 * here (the compiler names the missing one, as the `when` is exhaustive).
 */
@Composable
internal fun StatsSectionSlot(
    section: StatsSection,
    gameName: (GameId) -> String,
    onOpenSolve: (SolveRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (section) {
        is StatsSection.TimeTrend -> TrendSection(section.state, modifier)
        is StatsSection.Records -> RecordsSection(section.state, gameName, onOpenSolve, modifier)
        is StatsSection.HintFreeRate -> HintFreeSection(section.state, modifier)
        is StatsSection.Weekday -> WeekdaySection(section.state, modifier)
        is StatsSection.VsFriends -> VsFriendsSection(section.state, gameName, modifier)
    }
}
