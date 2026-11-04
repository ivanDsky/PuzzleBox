package com.ivandsky.puzzlebox.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.GameSummary
import com.ivandsky.puzzlebox.model.ThemeMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/** The left pane and the whole single-pane screen share one stacked content column. */
@Composable
internal fun ListPane(
    game: GameSummary,
    identity: Identity?,
    themeMode: ThemeMode,
    onOpenTheme: () -> Unit,
    onOpenHistory: () -> Unit,
    history: List<HistoryDay>,
    selectedDay: String?,
    onDayClick: (String) -> Unit,
    selectable: Boolean,
    onPlayToday: () -> Unit,
    onOpenProfile: () -> Unit,
    paneColor: Color,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(paneColor)
            .verticalScroll(rememberScrollState()),
    ) {
        LargeTopBar(
            title = "PuzzleBox",
            subtitle = todayHeadline(),
            actions = {
                IconButton(onClick = onOpenTheme) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Appearance",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        StaggeredItem(0) {
            TodayCard(
                game = game,
                onPlayToday = onPlayToday,
                cardColor = cardColor,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        StaggeredItem(1) {
            HistorySection(
                history = history,
                selectedDay = selectedDay,
                onDayClick = onDayClick,
                selectable = selectable,
                onOpenHistory = onOpenHistory,
                cardColor = cardColor,
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }
        StaggeredItem(2) {
            AccountCard(
                identity = identity,
                onOpenProfile = onOpenProfile,
                cardColor = cardColor,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xl, bottom = Spacing.xxl),
            )
        }
    }
}

@Composable
private fun HistorySection(
    history: List<HistoryDay>,
    selectedDay: String?,
    onDayClick: (String) -> Unit,
    selectable: Boolean,
    onOpenHistory: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your history",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(text = "See all", onClick = onOpenHistory)
        }
        if (history.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = cardColor,
            ) {
                EmptyState(
                    title = "No games yet",
                    message = "Finished puzzles and scores will appear here.",
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = cardColor,
            ) {
                Column(Modifier.padding(Spacing.sm)) {
                    history.forEach { day ->
                        HistoryRow(
                            day = day,
                            selected = selectable && day.isoDay == selectedDay,
                            onClick = if (selectable) {
                                { onDayClick(day.isoDay) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    day: HistoryDay,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = day.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Daily ${day.gameName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = true,
            shape = MaterialTheme.shapes.small,
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        ) { rowContent() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = Color.Transparent,
        ) { rowContent() }
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

private fun todayHeadline(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
