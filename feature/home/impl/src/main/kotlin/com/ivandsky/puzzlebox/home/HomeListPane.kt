package com.ivandsky.puzzlebox.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.GameId
import java.time.format.DateTimeFormatter

/**
 * The left pane and the whole single-pane screen share one stacked column on the app
 * background (identity §2.8): the header, then Today, the streak
 * card, the summary rows and the recent days, each entering in turn. A non-null
 * [HomeUiState.updateUrl] shows the [UpdatePill]: in the header's actions, or on its own line under
 * the header when [pillBelowHeader] (the phone header also holds the shell's avatar and the title
 * would wrap at 360dp); a tap hands the URL to [onOpenUpdate].
 */
@Composable
internal fun ListPane(
    state: HomeUiState,
    onOpenHistory: () -> Unit,
    selectedDay: String?,
    onDayClick: (String) -> Unit,
    selectable: Boolean,
    showStreak: Boolean,
    pillBelowHeader: Boolean,
    onOpenGame: (GameId) -> Unit,
    summaries: HomeSummaries,
    title: String,
    modifier: Modifier = Modifier,
    onOpenUpdate: (String) -> Unit = {},
) {
    val scroll = rememberScrollState()
    OnTabReselect { scroll.animateScrollTo(0) }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll),
    ) {
        LargeTopBar(
            title = title,
            subtitle = state.puzzleDay.format(HEADER_DATE),
            actions = {
                if (!pillBelowHeader) state.updateUrl?.let { url -> UpdatePill(onClick = { onOpenUpdate(url) }) }
            },
        )
        // Phone: the avatar already shares the header row, so the pill gets a line of its own. The check
        // can land after the first frame; the line grows in so Today glides down instead of jumping.
        if (pillBelowHeader) {
            AnimatedVisibility(
                visible = state.updateUrl != null,
                enter = expandVertically() + fadeIn(),
            ) {
                Box(Modifier.padding(start = Spacing.lg, bottom = Spacing.lg)) {
                    UpdatePill(onClick = { state.updateUrl?.let(onOpenUpdate) })
                }
            }
        }
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            StaggeredItem(0) {
                HomeTodaySection(state = state, onOpenGame = onOpenGame, tagPrefix = "home.today")
            }
            // Two-pane shows the streak in the detail pane instead.
            if (showStreak) {
                StaggeredItem(1) {
                    HomeStreakSection(stats = state.stats, week = state.week, total = state.today.size)
                }
            }
            // The summaries sit under the streak, one line each, only when there is something to say.
            HomeSummaryRows(state = state, summaries = summaries, index = 2)
            StaggeredItem(3) {
                HomeRecentSection(
                    days = state.history,
                    onSeeAll = onOpenHistory,
                    selectedDay = selectedDay,
                    onDayClick = onDayClick,
                    selectable = selectable,
                )
            }
        }
    }
}

private val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
