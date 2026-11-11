package com.ivandsky.puzzlebox.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.LocalTabReselect
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.GameId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The left pane and the whole single-pane screen share one stacked column on the app
 * background (identity §2.8): the header, then Today, the streak
 * card and the recent days, each entering in turn. A non-null
 * [HomeUiState.updateUrl] puts the [UpdatePill] in the header's actions; a tap hands it to [onOpenUpdate].
 */
@Composable
internal fun ListPane(
    state: HomeUiState,
    onOpenHistory: () -> Unit,
    selectedDay: String?,
    onDayClick: (String) -> Unit,
    selectable: Boolean,
    showStreak: Boolean,
    onOpenGame: (GameId) -> Unit,
    summaries: HomeSummaries,
    title: String,
    modifier: Modifier = Modifier,
    onOpenUpdate: (String) -> Unit = {},
) {
    val scroll = rememberScrollState()
    val reselect = LocalTabReselect.current
    LaunchedEffect(reselect, scroll) {
        reselect.collect { scroll.animateScrollTo(0) }
    }
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scroll),
    ) {
        LargeTopBar(
            title = title,
            subtitle = state.puzzleDay.format(HEADER_DATE),
            actions = {
                state.updateUrl?.let { url -> UpdatePill(onClick = { onOpenUpdate(url) }) }
            },
        )
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
            HomeSummaryRows(state = state, summaries = summaries)
            StaggeredItem(2) {
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

/**
 * First-composition entrance (handoff 05-b): a fade-only stagger, [MotionTokens.EnterStagger]
 * apart up to the cap. Reduced motion: one plain fade, no stagger.
 */
@Composable
private fun StaggeredItem(
    index: Int,
    content: @Composable () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    // A preview has no first frame to animate: it draws every item settled.
    val settled = LocalInspectionMode.current
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) delay(MotionTokens.EnterStagger.toLong() * index.coerceAtMost(MotionTokens.EnterStaggerCap))
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared || settled) 1f else 0f,
        animationSpec = if (reducedMotion) {
            tween(MotionTokens.ReducedFadeDuration)
        } else {
            tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate)
        },
        label = "home-entrance-$index",
    )
    Box(Modifier.graphicsLayer { alpha = progress }) { content() }
}

private val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
