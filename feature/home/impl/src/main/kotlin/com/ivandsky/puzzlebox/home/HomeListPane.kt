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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.GameId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * The left pane and the whole single-pane screen share one stacked column on the app
 * background (identity §2.8): the header with the theme gear, then Today, the streak
 * card, the recent days and the account card, each entering in turn.
 */
@Composable
internal fun ListPane(
    state: HomeUiState,
    onOpenTheme: () -> Unit,
    onOpenHistory: () -> Unit,
    selectedDay: String?,
    onDayClick: (String) -> Unit,
    selectable: Boolean,
    onOpenGame: (GameId) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
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
                IconButton(onClick = onOpenTheme) {
                    Icon(
                        imageVector = PuzzleIcons.Appearance,
                        contentDescription = "Appearance",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        Column(
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            StaggeredItem(0) {
                HomeTodaySection(state = state, onOpenGame = onOpenGame, tagPrefix = "home.today")
            }
            StaggeredItem(1) {
                HomeStreakSection(stats = state.stats, week = state.week, total = state.today.size)
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

/** First-composition entrance: items rise 12dp and fade in, staggered 60ms apart. */
@Composable
private fun StaggeredItem(
    index: Int,
    content: @Composable () -> Unit,
) {
    var appeared by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate),
        label = "home-entrance-$index",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 12.dp.toPx()
        },
    ) { content() }
}

private val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d")
