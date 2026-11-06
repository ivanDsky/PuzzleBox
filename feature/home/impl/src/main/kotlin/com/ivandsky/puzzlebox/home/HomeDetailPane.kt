package com.ivandsky.puzzlebox.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.GameId

/** Expanded-width detail pane: today's set until a history day is selected. */
@Composable
internal fun DetailPane(
    selected: HistoryDay?,
    state: HomeUiState,
    onOpenGame: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = selected,
        modifier = modifier
            .testTag("home.detail_pane"),
        // Shared axis (A12): arriving content decelerates in with a slight rise,
        // leaving content accelerates out — exit is the faster half.
        transitionSpec = {
            (
                fadeIn(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)) +
                    slideInHorizontally(tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingEmphasizedDecelerate)) { it / 12 }
                )
                .togetherWith(fadeOut(tween(MotionTokens.DurationShort, easing = MotionTokens.EasingEmphasizedAccelerate)))
        },
        label = "home-detail",
        contentKey = { it?.isoDay },
    ) { day ->
        if (day == null) {
            TodayDetail(state = state, onOpenGame = onOpenGame, modifier = Modifier.fillMaxSize())
        } else {
            DayDetail(day = day, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Today's set again, roomier: the two-pane hero (A15), with the same tiles and CTA. */
@Composable
private fun TodayDetail(
    state: HomeUiState,
    onOpenGame: (GameId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = "Today",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HomeTodaySection(
            state = state,
            onOpenGame = onOpenGame,
            tagPrefix = "home.detail_today",
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Text(
            text = "Every game has a new puzzle each day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DayDetail(
    day: HistoryDay,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            text = day.headline,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        day.games.forEach { game ->
            Text(
                text = "${game.gameName} ${game.resultText}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "You can play this day again from History.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        Spacer(Modifier.weight(1f))
    }
}
