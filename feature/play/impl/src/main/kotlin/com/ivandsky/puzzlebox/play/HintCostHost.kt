package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.gameui.HintCostLine
import com.ivandsky.puzzlebox.gameui.LocalHintCostLine
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.GameSetting
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.currentKoinScope

private fun hintCostSeen(game: GameId) = GameSetting(game, "hint_cost_seen", default = false)

/**
 * Offers [game]'s one-time hint cost line to the board in [content], backed by the per-game `hint_cost_seen` setting.
 * Nothing is offered until the stored value has loaded, so a returning player never sees the line again, and a graph
 * with no settings store offers none.
 */
@Composable
internal fun HintCostHost(game: GameId, content: @Composable () -> Unit) {
    val settings = currentKoinScope().getOrNull<GameSettingsRepository>()
    val scope = rememberCoroutineScope()
    val key = remember(game) { hintCostSeen(game) }
    val seen by produceState<Boolean?>(null, settings, key) {
        settings?.flag(key)?.collect { value = it }
    }
    val line = seen?.let { stored ->
        HintCostLine(seen = stored, markSeen = {
            scope.launch { withContext(NonCancellable) { settings?.setFlag(key, true) } }
        })
    }
    CompositionLocalProvider(LocalHintCostLine provides line, content)
}
