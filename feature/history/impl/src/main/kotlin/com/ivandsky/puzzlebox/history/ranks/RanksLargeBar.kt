package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.LargeTopBarSubtitle
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window

/** The active scope and window as the large bar's sub line ("Global · Day"), defaults too, so the bar keeps its two lines. */
internal fun rankSubLine(scope: RankScope, window: Window): String = "${scope.name} · ${window.name}"

/**
 * The tab's hub on Today's large top bar (MainTabsLargeBar): [title] (the board menu on the phone, the board's name on
 * the tablet) over the active scope and window, the tune icon at the end, then the phone's avatar. The sub line opens
 * tune as the label pill did; a null [onTune] (nothing to tune) leaves it plain text and drops the icon.
 */
@Composable
internal fun RanksLargeBar(state: RanksUiState, onTune: (() -> Unit)?, modifier: Modifier = Modifier, title: @Composable () -> Unit) {
    LargeTopBar(
        title = title,
        modifier = modifier,
        subtitle = {
            LargeTopBarSubtitle(
                rankSubLine(state.scope, state.window),
                Modifier.testTag("ranks.tune.label"),
                onClick = onTune,
                onClickLabel = "Tune ranks",
            )
        },
        actions = { if (onTune != null) TuneButton(onTune) },
    )
}
