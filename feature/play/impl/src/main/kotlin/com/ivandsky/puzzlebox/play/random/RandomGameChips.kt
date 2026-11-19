package com.ivandsky.puzzlebox.play.random

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.GameFilterChip

/** "Game" over a wrapping row of chips: Any first, then one per game in [games]. [game] null is Any; [enabled] false greys them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RandomGameChips(
    game: GameId?,
    games: List<RandomGame>,
    enabled: Boolean,
    onGame: (GameId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Game", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GameFilterChip(
                brand = GameBrand.Neutral,
                label = "Any",
                selected = game == null,
                onClick = { onGame(null) },
                modifier = Modifier.testTag("random.game.any"),
                enabled = enabled,
            )
            games.forEach {
                GameFilterChip(
                    brand = it.brand,
                    label = it.name,
                    selected = game == it.id,
                    onClick = { onGame(it.id) },
                    modifier = Modifier.testTag("random.game.${it.id.value}"),
                    enabled = enabled,
                )
            }
        }
    }
}
