package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.SingleFilterPage

/** The page of [page]: a SingleFilterPage over the matching options, with the state's current pick selected, [actions] for the pick and [onBack] for the arrow, the system Back and the 150 ms return. */
@Composable
internal fun RandomFilterPageContent(
    page: RandomFilterPage,
    state: RandomPuzzleState,
    games: List<RandomGame>,
    actions: RandomPuzzleActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (page) {
        RandomFilterPage.Game -> {
            val options = remember(games) { randomGameOptions(games) }
            SingleFilterPage(
                title = "Game",
                allLabel = "Any game",
                options = options,
                selected = state.game,
                onSelect = actions.onGame,
                onBack = onBack,
                modifier = modifier,
                tag = "random.filter.game",
                allTag = "random.game.any",
            )
        }
        RandomFilterPage.Difficulty -> SingleFilterPage(
            title = "Difficulty",
            allLabel = "Any difficulty",
            options = randomDifficultyOptions(),
            selected = state.difficulty,
            onSelect = actions.onDifficulty,
            onBack = onBack,
            modifier = modifier,
            tag = "random.filter.difficulty",
            allTag = "random.difficulty.Any",
        )
    }
}
