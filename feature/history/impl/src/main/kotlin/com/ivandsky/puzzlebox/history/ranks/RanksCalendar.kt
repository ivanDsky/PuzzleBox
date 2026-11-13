package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryCalendar
import com.ivandsky.puzzlebox.history.HistoryStatsHeader
import java.time.LocalDate

/**
 * History's calendar in the hub: the Mon–Sun week strip, which expands in place to the month, where the lifetime
 * [stats] (streak, solved, best, hint-free) slide in under it and fold away with it. It is History's own calendar
 * driven by [state], so it draws from local data alone: signed out, offline, the same. A day tapped in the month
 * reports through [onSelectDay] (null: the selected day again, back to today); [onShowCalendar] pages it.
 */
@Composable
internal fun RanksCalendar(
    state: CalendarState,
    stats: GameStats?,
    onShowCalendar: (day: LocalDate, expanded: Boolean) -> Unit,
    onSelectDay: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.shownDay == null || state.today == null) return
    val reducedMotion = rememberReducedMotion()
    val enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard)) +
        expandVertically(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
    val exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(MotionTokens.DurationExit)) +
        shrinkVertically(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard))
    Column(modifier.padding(bottom = Spacing.md).testTag("ranks.calendar"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HistoryCalendar(
            state = state,
            onShowCalendar = onShowCalendar,
            onSelectDay = onSelectDay,
            cardColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
        AnimatedVisibility(visible = state.expanded && stats != null, enter = enter, exit = exit) {
            // Held through the fold, so the strip leaves with its numbers.
            stats?.let { HistoryStatsHeader(it, cardColor = MaterialTheme.colorScheme.surfaceContainerLow) }
        }
    }
}
