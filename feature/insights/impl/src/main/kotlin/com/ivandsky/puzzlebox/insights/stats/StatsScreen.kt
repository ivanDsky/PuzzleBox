package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.TwoColumnMinWidth
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.model.GameId

/**
 * The stats page's whole screen for [state]: a bar named for the game (or "All games"), the range chips and the
 * sections. With the StatsFilters flag on the phone's bar says "Stats" over a filter bar instead of the chips, and a wide window
 * names the scope "Overall" or the game with a quiet Since atop the pane. [onSelectRange] reports a chip, [onOpenSolve] the solve a record opens, [onBack] leaves the page. A window
 * wide enough for two columns ([TwoColumnMinWidth]) adds the list of [games] on the left and [onSelectGame] switches it.
 */
@Composable
internal fun StatsScreen(
    state: StatsUiState,
    games: List<GameId>,
    gameName: (GameId) -> String,
    onSelectRange: (StatsRange) -> Unit,
    onSelectGame: (GameId?) -> Unit,
    onOpenSolve: (SolveRef) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("stats")) {
        val wide = maxWidth >= TwoColumnMinWidth
        Column(Modifier.fillMaxSize()) {
            CompactTopBar(
                title = title(state, wide, gameName),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("stats.back")) {
                        Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
            if (wide) {
                StatsTwoPane(state, games, gameName, onSelectGame, onSelectRange, onOpenSolve)
            } else if (state.filters) {
                StatsContent(state, gameName, onSelectRange, onOpenSolve) {
                    StatsFilterBar(state, games, gameName, onSelectGame, onSelectRange)
                }
            } else {
                StatsContent(state, gameName, onSelectRange, onOpenSolve)
            }
        }
    }
}

/** The bar's title: "Stats" over the phone's filter bar, which names the game itself; else the scope, "Overall" or the game, and "stats". */
private fun title(state: StatsUiState, wide: Boolean, gameName: (GameId) -> String): String = when {
    state.filters && !wide -> "Stats"
    else -> "${state.game?.let(gameName) ?: if (state.filters) "Overall" else "All games"} stats"
}
