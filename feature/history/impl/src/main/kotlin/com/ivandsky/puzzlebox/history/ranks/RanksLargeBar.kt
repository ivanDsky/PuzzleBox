package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.LargeTopBarSubtitle
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Games
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window

/**
 * The active scope and window as the large bar's sub line ("Global · Day"), defaults too, so the bar keeps its two lines;
 * LinkedIn [games] adds a third part ("Global · Day · LinkedIn games"), All games adds nothing.
 */
internal fun rankSubLine(scope: RankScope, window: Window, games: Games = Games.All): String =
    "${scope.name} · ${window.name}" + if (games == Games.LinkedIn) " · ${games.label}" else ""

/**
 * The tab's hub on Today's large top bar (MainTabsLargeBar): [title] (the board menu on the phone, the board's name on
 * the tablet) over the active scope and window, then the phone's avatar.
 */
@Composable
internal fun RanksLargeBar(state: RanksUiState, modifier: Modifier = Modifier, title: @Composable () -> Unit) {
    LargeTopBar(
        title = title,
        modifier = modifier,
        subtitle = { LargeTopBarSubtitle(rankSubLine(state.scope, state.window, state.games), Modifier.testTag("ranks.tune.label")) },
    )
}
