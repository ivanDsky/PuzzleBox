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
 * sections. [onSelectRange] reports a chip, [onOpenSolve] the solve a record opens, [onBack] leaves the page. A window
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
    Column(modifier.fillMaxSize().testTag("stats")) {
        CompactTopBar(
            title = "${state.game?.let(gameName) ?: "All games"} stats",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("stats.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= TwoColumnMinWidth) {
                StatsTwoPane(state, games, gameName, onSelectGame, onSelectRange, onOpenSolve)
            } else {
                StatsContent(state, gameName, onSelectRange, onOpenSolve)
            }
        }
    }
}
