package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppModalBottomSheet
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.DifficultyIcon
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameFilterChip
import com.ivandsky.puzzlebox.model.GameId

/**
 * The community filter as a bottom sheet: one game (All, then each of [games]) and one difficulty (Any, then each
 * [DifficultyChoice]), as chips. A tap reports through [onGame] / [onDifficulty] and the sheet stays open, so both can
 * be set and the list behind watched changing; [onDismiss] closes it. The tags are the ⋮ menu's items', so one test
 * addresses either.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityFilterSheet(
    games: List<GameChoice>,
    game: GameId?,
    difficulty: DifficultyChoice?,
    onGame: (GameId?) -> Unit,
    onDifficulty: (DifficultyChoice?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opens whole, as the Random sheet and the Tune sheet do, so the chips are not cut off at a half-height rest.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier.testTag("community.filter.sheet"), sheetState = sheetState) {
        CommunityFilterChoices(games, game, difficulty, onGame, onDifficulty)
    }
}

/** The sheet's body: its title, the Game chips and the Difficulty chips, each group a wrapping row. */
@Composable
internal fun CommunityFilterChoices(
    games: List<GameChoice>,
    game: GameId?,
    difficulty: DifficultyChoice?,
    onGame: (GameId?) -> Unit,
    onDifficulty: (DifficultyChoice?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Filter puzzles", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        ChipGroup("Game") {
            GameFilterChip(
                brand = GameBrand.Neutral,
                label = "All",
                selected = game == null,
                onClick = { onGame(null) },
                modifier = Modifier.testTag("community.filter.game.all"),
            )
            games.forEach { choice ->
                GameFilterChip(
                    brand = choice.brand,
                    label = choice.name,
                    selected = game == choice.id,
                    onClick = { onGame(choice.id) },
                    modifier = Modifier.testTag("community.filter.game.${choice.id.value}"),
                )
            }
        }
        ChipGroup("Difficulty") {
            GameFilterChip(
                brand = GameBrand.Neutral,
                label = "Any",
                selected = difficulty == null,
                onClick = { onDifficulty(null) },
                modifier = Modifier.testTag("community.filter.difficulty.any"),
            )
            DifficultyChoice.entries.forEach { choice ->
                GameFilterChip(
                    brand = GameBrand.Neutral,
                    label = choice.label,
                    selected = difficulty == choice,
                    onClick = { onDifficulty(choice) },
                    modifier = Modifier.testTag("community.filter.difficulty.${choice.name.lowercase()}"),
                    icon = { DifficultyIcon(choice.mark) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            chips()
        }
    }
}
