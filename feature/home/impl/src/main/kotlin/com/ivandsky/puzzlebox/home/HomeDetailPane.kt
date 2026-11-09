package com.ivandsky.puzzlebox.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing

/** Expanded-width detail pane: streak & stats until a recent day is selected, then that day. */
@Composable
internal fun DetailPane(
    selected: HistoryDay?,
    state: HomeUiState,
    hideOwnedElsewhere: Boolean,
    modifier: Modifier = Modifier,
) {
    val risePx = with(LocalDensity.current) { MotionTokens.MixTabRise.roundToPx() }
    val background = MaterialTheme.colorScheme.background
    val rise = remember(risePx) {
        // Rise-over (polish 04, D): the arriving pane fades in and rises over the leaving
        // one, which stays opaque until covered, so two panes never fade at once.
        fadeIn(tween(MotionTokens.MixTabIn, easing = MotionTokens.EasingStandard)) +
            slideInVertically(tween(MotionTokens.MixTabIn, easing = MotionTokens.EasingStandard)) { risePx }
    }
    AnimatedContent(
        // Hidden: the day detail does not exist, so the pane is always the streak and stats.
        targetState = selected.takeUnless { hideOwnedElsewhere },
        modifier = modifier
            .testTag("home.detail_pane"),
        // The leaving pane does not animate; it is only held, beneath, until the rise lands.
        transitionSpec = { rise togetherWith ExitTransition.KeepUntilTransitionsFinished },
        label = "home-detail",
        contentKey = { it?.isoDay },
    ) { day ->
        if (day == null) {
            HomeStreakPane(
                state = state,
                modifier = Modifier.fillMaxSize().padding(Spacing.sm),
                showBests = !hideOwnedElsewhere,
            )
        } else {
            // Opaque like the streak pane, so the pane beneath never shows through as it rises.
            DayDetail(day = day, modifier = Modifier.fillMaxSize().background(background))
        }
    }
}

@Composable
private fun DayDetail(
    day: HistoryDay,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.xxl).testTag("home.day_detail"),
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
