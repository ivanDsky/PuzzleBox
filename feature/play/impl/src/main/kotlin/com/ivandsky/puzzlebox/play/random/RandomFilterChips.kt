package com.ivandsky.puzzlebox.play.random

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.FilterChipRow
import com.ivandsky.puzzlebox.designsystem.FilterOption
import com.ivandsky.puzzlebox.designsystem.FilterPresentation
import com.ivandsky.puzzlebox.designsystem.SingleFilterChip
import com.ivandsky.puzzlebox.gameui.gameFilterOption
import com.ivandsky.puzzlebox.gameui.levelFilterOption
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.DifficultyChoice

/** The two Random filters; the host that holds one is showing that filter's option page. */
internal enum class RandomFilterPage { Game, Difficulty }

private val DifficultyOptions = DifficultyChoice.entries.map { levelFilterOption(it, it.label, it.mark, tag = "random.difficulty.${it.label}") }

/** The option lists the chip and its page both read, so they cannot drift. Game: glyphs from `GameFilterOption`; Difficulty: Easy, Medium, Hard with their level marks. */
internal fun randomGameOptions(games: List<RandomGame>): List<FilterOption<GameId>> =
    games.map { gameFilterOption(it.id, it.name, it.brand, tag = "random.game.${it.id.value}") }

internal fun randomDifficultyOptions(): List<FilterOption<DifficultyChoice>> = DifficultyOptions

/** Game and, for New today, Difficulty as single-pick chips, always [FilterPresentation.Inline]: a click calls [onOpen] and the host swaps its content for the page. [enabled] false (a pick is running) greys and stops them. */
@Composable
internal fun RandomFilterChips(
    state: RandomPuzzleState,
    games: List<RandomGame>,
    enabled: Boolean,
    actions: RandomPuzzleActions,
    onOpen: (RandomFilterPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gameOptions = remember(games) { randomGameOptions(games) }
    val openGame = remember(onOpen) { FilterPresentation.Inline { onOpen(RandomFilterPage.Game) } }
    val openDifficulty = remember(onOpen) { FilterPresentation.Inline { onOpen(RandomFilterPage.Difficulty) } }
    FilterChipRow(modifier) {
        SingleFilterChip(
            name = "Game",
            allLabel = "Any game",
            options = gameOptions,
            selected = state.game,
            onSelect = actions.onGame,
            enabled = enabled,
            presentation = openGame,
            tag = "random.filter.game",
            allTag = "random.game.any",
        )
        if (state.source == RandomSource.Community) {
            SingleFilterChip(
                name = "Difficulty",
                allLabel = "Any difficulty",
                options = randomDifficultyOptions(),
                selected = state.difficulty,
                onSelect = actions.onDifficulty,
                enabled = enabled,
                presentation = openDifficulty,
                tag = "random.filter.difficulty",
                allTag = "random.difficulty.Any",
            )
        }
    }
}
