package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.label
import com.ivandsky.puzzlebox.insights.share.RecapShareViewModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayKey
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** The recap route with the game brands the friends card stamps, from the registry. */
@Composable
internal fun RecapRoute(periodId: String, onOpenReplay: (ReplayKey) -> Unit, onClose: () -> Unit) {
    val brands = koinInject<GamePlayRegistry>()
    CompositionLocalProvider(LocalGameBrands provides brands::brandOf) {
        RecapStoryRoute(periodId, onOpenReplay, onClose)
    }
}

/**
 * A recap's route: the story of the period [periodId] names. An id this build cannot read (a stale or hand-edited key)
 * is a period with nothing to recap, not a crash. [onOpenReplay] gets the key of the replay a Fastest solve row opens:
 * the first win of that game's daily, which is the solve the card counted. A recap that can be read ends on the Share
 * page, whose chooser this route starts.
 */
@Composable
private fun RecapStoryRoute(periodId: String, onOpenReplay: (ReplayKey) -> Unit, onClose: () -> Unit) {
    val registry = koinInject<GameRegistry>()
    val gameName = remember(registry) { { game: GameId -> registry.displayName(game) } }
    val watchReplay: (RecapCard.Fastest.Row) -> Unit = { row ->
        onOpenReplay(ReplayKey.daily(row.game.value, row.day.toString(), row.puzzleNumber, latest = false))
    }
    val playRegistry = koinInject<GamePlayRegistry>()
    val accentOf = remember(playRegistry) { { game: GameId -> playRegistry.brandOf(game).accent } }
    CompositionLocalProvider(LocalGameAccent provides accentOf) { RecapContent(periodId, gameName, watchReplay, onClose) }
}

@Composable
private fun RecapContent(
    periodId: String,
    gameName: (GameId) -> String,
    watchReplay: (RecapCard.Fastest.Row) -> Unit,
    onClose: () -> Unit,
) {
    val period = RecapPeriod.parse(periodId)
    if (period == null) {
        RecapScreen(RecapUiState.Empty, periodLabel = "Recap", gameName = gameName, onPage = {}, onWatchReplay = watchReplay, onClose = onClose)
    } else {
        val viewModel = koinViewModel<RecapViewModel>(parameters = { parametersOf(period) })
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val shareViewModel = koinViewModel<RecapShareViewModel>(parameters = { parametersOf(period) })
        val share by shareViewModel.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        LaunchedEffect(shareViewModel) { shareViewModel.requests.collect(context::startActivity) }
        RecapScreen(
            state, period.label(), gameName,
            onPage = viewModel::showPage,
            onWatchReplay = watchReplay,
            onClose = onClose,
            displayName = share.displayName?.takeIf { it.isNotBlank() },
            share = share,
            onShare = shareViewModel::share,
            onRetry = viewModel::retry,
        )
    }
}
