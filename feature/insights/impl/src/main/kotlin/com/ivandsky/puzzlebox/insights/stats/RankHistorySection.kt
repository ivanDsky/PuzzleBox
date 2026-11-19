package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.chart.LineChartPoint
import com.ivandsky.puzzlebox.insights.domain.stats.RankHistoryStanding
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsGranularity
import com.ivandsky.puzzlebox.insights.domain.stats.VsFriendsStanding
import java.util.Locale

/**
 * The rank history section: the viewer's Friends points per kept week, always weekly and whatever the range, as a line
 * from 2 weeks and as one number with 1 (A14). Each value reads "among your friends that week" (A19): a week is what
 * it was when kept. With no week kept yet it says when they start to appear. While it loads or cannot load it is the
 * usual titled frame with its quiet line (A16). Signed out, or with no friends, the section is replaced by one plain
 * line, with no title, saying what unlocks it. [singleGame] is true on a single game's page; the all-games page adds the games up.
 */
@Composable
internal fun RankHistorySection(state: SectionState<RankHistoryStanding>, singleGame: Boolean, modifier: Modifier = Modifier) {
    val locked = (state as? SectionState.Ready)?.value as? RankHistoryStanding.Locked
    if (locked != null) {
        StatsUnlockLine(locked.unlock.line(), "stats.rankHistory.locked", modifier)
        return
    }
    StatsSectionFrame(title = "Rank history", state = state, tag = "stats.rankHistory", modifier = modifier) { standing ->
        if (standing is RankHistoryStanding.Kept) KeptWeeks(standing, singleGame)
    }
}

private fun VsFriendsStanding.Unlock.line(): String = when (this) {
    VsFriendsStanding.Unlock.SignIn -> "Sign in and add friends to see your Friends points week by week."
    VsFriendsStanding.Unlock.AddFriends -> "Add friends to see your Friends points week by week."
}

@Composable
private fun KeptWeeks(kept: RankHistoryStanding.Kept, singleGame: Boolean) {
    if (kept.weeks.isEmpty()) {
        Text(
            text = "Your weeks show here once a week you played with friends has ended.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("stats.rankHistory.empty"),
        )
        return
    }
    val withYear = kept.weeks.map { it.start.year }.distinct().size > 1
    val latest = kept.weeks.last()
    val what = "Friends points per day" + if (singleGame) "" else ", all games added up"
    val among = "among your friends that week"
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatsTrendBlock(
            tag = "stats.rankHistory",
            what = "$what, $among",
            line = if (kept.isLine) kept.weeks.map { LineChartPoint(it.start.toEpochDay().toDouble(), it.pointsPerDay, it.start.axisLabel(withYear)) } else null,
            valueLabel = ::points,
            singleValue = points(latest.pointsPerDay),
            singleDetail = latest.start.bucketLabel(StatsGranularity.Week),
        )
        if (kept.isLine) {
            Text(
                text = "$what, $among",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("stats.rankHistory.caption"),
            )
        }
    }
}

private fun points(value: Double): String = String.format(Locale.ROOT, "%,.1f", value)
