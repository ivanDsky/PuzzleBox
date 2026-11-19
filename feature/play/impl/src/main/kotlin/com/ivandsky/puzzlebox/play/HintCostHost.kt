package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.gameui.HintCostLine
import com.ivandsky.puzzlebox.gameui.LocalHintCostLine
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.GameSetting
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.currentKoinScope

private fun hintCostSeen(game: GameId) = GameSetting(game, "hint_cost_seen", default = false)

/**
 * Offers [game]'s one-time hint cost line to the board in [content], backed by the per-game `hint_cost_seen` setting;
 * [inputs] is the play's player-input count, which clears the line. Nothing is offered, and no setting is read, with
 * no settings store, and nothing until the stored value has loaded, so a returning player never sees
 * the line again. The one [HintCostLine] reads state, so the board below never recomposes for it.
 */
@Composable
internal fun HintCostHost(game: GameId, inputs: StateFlow<Int>, content: @Composable () -> Unit) {
    val settings = currentKoinScope().getOrNull<GameSettingsRepository>()
    val scope = rememberCoroutineScope()
    val key = remember(game) { hintCostSeen(game) }
    val seen: State<Boolean?> = produceState<Boolean?>(null, settings, key) {
        settings?.flag(key)?.collect { value = it }
    }
    val inputCount = inputs.collectAsState()
    val line = remember(settings, key) {
        settings?.let {
            HintCostLine(
                seen = { seen.value != false }, // unloaded reads as seen: nothing shows until the store answers
                inputs = { inputCount.value },
                markSeen = { scope.launch { withContext(NonCancellable) { it.setFlag(key, true) } } },
            )
        }
    }
    CompositionLocalProvider(LocalHintCostLine provides line, content)
}
