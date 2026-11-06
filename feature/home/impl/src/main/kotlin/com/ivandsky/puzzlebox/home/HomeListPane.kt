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
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.GameId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The left pane and the whole single-pane screen share one stacked column on the app
 * background (identity §2.8): the header, then Today, the streak
 * card, the recent days and the account card, each entering in turn. A non-null
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
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenUpdate: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        LargeTopBar(
            title = "PuzzleBox",
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
            StaggeredItem(2) {
                HomeRecentSection(
                    days = state.history,
                    onSeeAll = onOpenHistory,
                    selectedDay = selectedDay,
                    onDayClick = onDayClick,
                    selectable = selectable,
                )
            }
            StaggeredItem(3) {
                AccountCard(identity = state.identity, onOpenProfile = onOpenProfile)
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
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) delay(MotionTokens.EnterStagger.toLong() * index.coerceAtMost(MotionTokens.EnterStaggerCap))
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
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
