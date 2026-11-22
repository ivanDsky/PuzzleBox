package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.insights.recap.LocalGameBrands
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayKey
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/**
 * The stats page's route for the game [gameId] names, or every game when null. A record opens the solve it came from
 * (A13): its replay through [onOpenReplay], or, for a result with none (an import), History at that game and day
 * through [onOpenDay].
 */
@Composable
internal fun StatsRoute(
    gameId: String?,
    onOpenReplay: (ReplayKey) -> Unit,
    onOpenDay: (GameId, LocalDate) -> Unit,
    onBack: () -> Unit,
) {
    val registry = koinInject<GameRegistry>()
    val playRegistry = koinInject<GamePlayRegistry>()
    val viewModel = koinViewModel<StatsViewModel>(parameters = { parametersOf(gameId) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val games = remember(registry) { registry.all.map { it.definition.id } }
    CompositionLocalProvider(LocalGameBrands provides playRegistry::brandOf) {
        StatsScreen(
            state = state,
            games = games,
            gameName = { registry.displayName(it) },
            onSelectRange = viewModel::selectRange,
            onSelectGame = viewModel::selectGame,
            onOpenSolve = { solve -> openSolve(solve, onOpenReplay, onOpenDay) },
            onBack = onBack,
        )
    }
}

/** Opens [solve]: its replay when it has one, else the day it was played on. */
internal fun openSolve(solve: SolveRef, onOpenReplay: (ReplayKey) -> Unit, onOpenDay: (GameId, LocalDate) -> Unit) {
    if (solve.hasReplay) {
        onOpenReplay(ReplayKey.daily(solve.game.value, solve.day.toString(), solve.puzzleNumber, latest = false))
    } else {
        onOpenDay(solve.game, solve.day)
    }
}
