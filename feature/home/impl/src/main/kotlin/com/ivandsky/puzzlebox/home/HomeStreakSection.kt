package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.resultui.FreezeCount
import com.ivandsky.puzzlebox.resultui.WeekStrip

/**
 * The streak card (A8, identity §2.8): "N days in a row · Best M" from [stats] over the
 * [WeekStrip] of the last 7 puzzle days ([week], oldest first, ending today), each day's
 * game ring counting its solved dailies out of [total] games. The [FreezeCount] of
 * [freezesHeld] sits after the line, and a frozen day shows its snowflake. Until [loaded] (Home's local state has
 * landed) the line is an empty slot of its height, never "0 days in a row · Best 0" (A2). [onOpenStreak] makes the
 * streak row a button and [onOpenDay] each week disc a button that reports its ISO day (nav rethink N2, N3).
 */
@Composable
internal fun HomeStreakSection(
    stats: GameStats,
    loaded: Boolean,
    freezesHeld: Int?,
    week: List<WeekDay>,
    total: Int,
    onOpenStreak: () -> Unit,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth().testTag("home.streak"),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            StreakDoor(
                onOpen = onOpenStreak,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = PuzzleIcons.Flame,
                    contentDescription = null,
                    tint = LocalPrimaryText.current,
                    modifier = Modifier.size(20.dp),
                )
                if (loaded) {
                    Text(
                        text = streakLine(stats),
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    BlankValue(MaterialTheme.typography.titleMedium.tabular())
                }
                freezesHeld?.let { FreezeCount(held = it) }
            }
            WeekStrip(
                days = week,
                total = total,
                modifier = Modifier.testTag("home.week"),
                onDayClick = { date -> onOpenDay(date.toString()) },
            )
        }
    }
}

/** "3 days in a row · Best 5" ("1 day" for one). */
private fun streakLine(stats: GameStats): String {
    val days = if (stats.currentStreak == 1) "day" else "days"
    return "${stats.currentStreak} $days in a row · Best ${stats.bestStreak}"
}
