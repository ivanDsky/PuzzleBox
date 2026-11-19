package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.label
import com.ivandsky.puzzlebox.model.GameId
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * A recap's route: the story of the period [periodId] names. An id this build cannot read (a stale or hand-edited key)
 * is a period with nothing to recap, not a crash.
 */
@Composable
internal fun RecapRoute(periodId: String, onClose: () -> Unit) {
    val registry = koinInject<GameRegistry>()
    val gameName = remember(registry) { { game: GameId -> registry.displayName(game) } }
    val period = RecapPeriod.parse(periodId)
    if (period == null) {
        RecapScreen(RecapUiState.Empty, periodLabel = "Recap", gameName = gameName, onPage = {}, onClose = onClose)
    } else {
        val viewModel = koinViewModel<RecapViewModel>(parameters = { parametersOf(period) })
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        RecapScreen(state, period.label(), gameName, onPage = viewModel::showPage, onClose = onClose)
    }
}
