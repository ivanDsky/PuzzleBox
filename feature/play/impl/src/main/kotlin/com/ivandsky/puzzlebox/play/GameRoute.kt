package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.gameui.PlayBoardFallback
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.GameKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * One [GameKey]'s board on any stack (A9, N21): the entry's [GameViewModel] opens the
 * slot's live play in the [PlaySessionStore], and this draws its board and status under a
 * top bar whose Up is [onBack], the same navigator pop as system back. The bar carries no
 * title (the board draws its own header) unless the board was kept across its game's
 * midnight, when it reads "Yesterday's puzzle". There is no BackHandler (predictive back
 * stays the scene's) and no saveable blob: the store's slot row is what survives process
 * death (A8, A11).
 */
@Composable
internal fun GameRoute(
    key: GameKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (slot: PlaySlot, timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GameViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val play = viewModel.play
    val pastDay by viewModel.pastDay.collectAsStateWithLifecycle()
    Column(modifier = modifier.testTag("route.game")) {
        CompactTopBar(
            title = if (pastDay) PAST_DAY else "",
            modifier = if (pastDay) Modifier.testTag("game.past_day") else Modifier,
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("game.up")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        if (play == null) {
            PlayBoardFallback(PlayBoardStatus.Unavailable(UNPLAYABLE), onRetry = {}, tagPrefix = "game")
        } else {
            val board by play.board.collectAsStateWithLifecycle()
            play.ui.Screen(
                controller = board.controller,
                status = board.status,
                layout = layout,
                onRetry = play::retry,
                onSolved = { timeMs, hintsUsed -> onSolved(play.slot, timeMs, hintsUsed) },
                modifier = Modifier,
            )
        }
    }
}

private const val UNPLAYABLE = "This game isn't available in this build."
private const val PAST_DAY = "Yesterday's puzzle"
