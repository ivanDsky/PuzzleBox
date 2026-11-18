package com.ivandsky.puzzlebox.play.random

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayLayout

/**
 * The random sheet's body: its title and the Past puzzles controls, with the pick's status line under the button. One
 * column on a phone; on [PlayLayout.TwoPane] two: the Game chips on the left, "Not solved yet", Pick and the status on
 * the right. Chips and the switch are off while a pick runs or its puzzle opens.
 */
@Composable
internal fun RandomPuzzleContent(
    state: RandomPuzzleState,
    games: List<RandomGame>,
    layout: PlayLayout,
    actions: RandomPuzzleActions,
    modifier: Modifier = Modifier,
) {
    val busy = state.phase is RandomPhase.Picking || state.phase is RandomPhase.Opened
    val chips: @Composable (Modifier) -> Unit = { RandomGameChips(state.game, games, !busy, actions.onGame, it) }
    val pick: @Composable (Modifier) -> Unit = { RandomPickControls(state, busy, actions, it) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Random puzzle", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (layout == PlayLayout.TwoPane) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                chips(Modifier.weight(1f).testTag("random.left"))
                pick(Modifier.weight(1f).testTag("random.right"))
            }
        } else {
            chips(Modifier)
            pick(Modifier)
        }
    }
}

/** "Not solved yet", the Pick button and the status line, one under the other. */
@Composable
private fun RandomPickControls(state: RandomPuzzleState, busy: Boolean, actions: RandomPuzzleActions, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinRowHeight)
                .toggleable(value = state.unsolvedOnly, enabled = !busy, role = Role.Switch, onValueChange = actions.onUnsolved)
                .testTag("random.unsolved"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Not solved yet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Switch(checked = state.unsolvedOnly, onCheckedChange = null, enabled = !busy)
        }
        PrimaryButton(
            text = "Pick one for me",
            onClick = actions.onPick,
            modifier = Modifier.fillMaxWidth().testTag("random.pick"),
            enabled = !busy,
            loading = state.phase is RandomPhase.Picking,
        )
        (state.phase as? RandomPhase.Said)?.let { RandomStatusText(it.message) }
    }
}

private val MinRowHeight = 48.dp
