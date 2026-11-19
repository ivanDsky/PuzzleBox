package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.model.GameId

/**
 * The stats page on a wide window: every game and the games in the app's order down the left, the picked one's page on
 * the right. The list is the picker the phone page does not need, as a phone opens the page for one game already.
 */
@Composable
internal fun StatsTwoPane(
    state: StatsUiState,
    games: List<GameId>,
    gameName: (GameId) -> String,
    onSelectGame: (GameId?) -> Unit,
    onSelectRange: (StatsRange) -> Unit,
    onOpenSolve: (SolveRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize().testTag("stats.twoPane")) {
        Column(
            Modifier
                .width(GameListWidth)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg)
                .testTag("stats.games"),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            GameChoice("All games", picked = state.game == null, tag = "stats.game.all") { onSelectGame(null) }
            games.forEach { game ->
                GameChoice(gameName(game), picked = state.game == game, tag = "stats.game.${game.value}") { onSelectGame(game) }
            }
        }
        StatsContent(state, gameName, onSelectRange, onOpenSolve, Modifier.weight(1f))
    }
}

/** One entry of the game list, drawn picked when it is the page's scope. */
@Composable
private fun GameChoice(label: String, picked: Boolean, tag: String, onClick: () -> Unit) {
    val fill = if (picked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val ink = if (picked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.large)
            .background(fill)
            .selectable(selected = picked, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Spacing.md)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = ink)
    }
}

private val GameListWidth = 240.dp
