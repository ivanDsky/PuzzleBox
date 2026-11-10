package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.LocalRouteCaption
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.PlayOverflowItem
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One duel puzzle's board (prototype 02): the [MatchLegViewModel]'s live play of the key's leg
 * under the normal play chrome, without a tutorial gate or How to play. The game id rides the
 * key, so the board opens, after a kill too, without the match.
 *
 * A taken puzzle never reopens: the ViewModel's check sends the route to [onTaken] with my
 * recorded result, and the board stays a loading screen until then.
 *
 * Once the match is known the caption reads "Duel vs {opponent} · puzzle {n} of {N}"
 * ([LocalRouteCaption], so a game's own caption still wins for its moment). Signed in, the
 * bar's menu adds Give up, confirmed in a dialog: confirming queues the loss and calls
 * [onGaveUp]; Keep playing, the scrim and Back keep playing. Signed out there is no Give up,
 * as nothing could record it.
 */
@Composable
internal fun GameRoute(
    key: MatchLegKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    onGaveUp: () -> Unit,
    onTaken: (MatchLegResultKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MatchLegViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val currentOnTaken by rememberUpdatedState(onTaken)
    val currentOnGaveUp by rememberUpdatedState(onGaveUp)
    LaunchedEffect(viewModel) {
        viewModel.exits.collect { exit ->
            when (exit) {
                is MatchLegExit.Taken -> currentOnTaken(exit.result)
                MatchLegExit.GaveUp -> currentOnGaveUp()
            }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playing = state as? MatchLegState.Playing
    if (playing == null) {
        GameLoading(onBack)
        return
    }

    var confirming by rememberSaveable { mutableStateOf(false) }
    val giveUp = remember(viewModel) { listOf(PlayOverflowItem(GIVE_UP, checked = null) { confirming = true }) }

    CompositionLocalProvider(LocalRouteCaption provides playing.position?.caption()) {
        GameBoard(
            play = playing.play,
            pastDay = false,
            tutorial = TutorialGate.Closed,
            onHowToPlay = null,
            onTutorialFinished = {},
            layout = layout,
            onBack = onBack,
            onSolved = { _, timeMs, hintsUsed -> onSolved(timeMs, hintsUsed) },
            overflow = if (playing.canGiveUp) giveUp else emptyList(),
            modifier = modifier,
        )
    }
    if (confirming && playing.canGiveUp) {
        GiveUpDialog(
            onConfirm = {
                confirming = false
                viewModel.giveUp()
            },
            onCancel = { confirming = false },
        )
    }
}

private fun LegPosition.caption(): String = "Duel${opponent?.let { " vs $it" }.orEmpty()} · puzzle $number of $count"

/** The Give up confirm: a loss that can't be restarted. Keep playing, the scrim and Back cancel. */
@Composable
private fun GiveUpDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AppDialog(
        title = "Give up this puzzle?",
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("leg.give_up.dialog"),
        content = {
            Text(
                text = "It counts as a loss for you, and you can't start it again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(text = "Keep playing", onClick = onCancel, modifier = Modifier.testTag("leg.give_up.cancel"))
                PrimaryButton(
                    text = GIVE_UP,
                    onClick = onConfirm,
                    destructive = true,
                    modifier = Modifier.testTag("leg.give_up.confirm"),
                )
            }
        },
    )
}

private const val GIVE_UP = "Give up"
