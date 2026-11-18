package com.ivandsky.puzzlebox.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.GameId

/**
 * Expanded-width detail pane: streak & stats until a recent day is selected, then that day.
 * [onOpenStreak] and [onOpenDay] (an ISO day) are the doors to Ranks from the streak pane and from
 * the day's link (nav rethink N2-N4). The day's Finish and Play open a game on that day through
 * [onOpenGameDay]; [onSignIn] is its signed-out link.
 */
@Composable
internal fun DetailPane(
    selected: HistoryDay?,
    state: HomeUiState,
    onOpenStreak: () -> Unit,
    onOpenDay: (String) -> Unit,
    onOpenGameDay: (GameId, String) -> Unit,
    onSignIn: () -> Unit,
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
        targetState = selected,
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
                onOpenStreak = onOpenStreak,
                onOpenDay = onOpenDay,
            )
        } else {
            // Opaque like the streak pane, so the pane beneath never shows through as it rises.
            HomeDayDetail(
                day = day,
                signedIn = state.identity != null,
                onOpenGame = onOpenGameDay,
                onSignIn = onSignIn,
                onOpenDay = onOpenDay,
                modifier = Modifier.fillMaxSize().background(background).padding(Spacing.sm),
            )
        }
    }
}
