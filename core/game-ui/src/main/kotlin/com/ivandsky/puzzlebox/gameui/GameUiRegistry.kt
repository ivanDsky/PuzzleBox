package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.model.GameId

/**
 * Maps a game's pure [RendererModel] to its Compose board (finding F2-1: the Compose
 * mapping lives game-ui side, registered per [GameId] — no Compose in :core:game, no
 * game-module imports here). Games register at app composition time (ADR-0001 Koin
 * aggregation); the play shell looks its game up by id.
 */
fun interface GameBoardRenderer {
    @Composable
    fun Content(model: RendererModel, modifier: Modifier)
}

class GameUiRegistry {
    private val byGame = HashMap<GameId, GameBoardRenderer>()

    /** First registration wins; duplicates are a wiring bug, not a silent overwrite. */
    fun register(gameId: GameId, renderer: GameBoardRenderer) {
        check(gameId !in byGame) { "renderer already registered for $gameId" }
        byGame[gameId] = renderer
    }

    operator fun get(gameId: GameId): GameBoardRenderer? = byGame[gameId]
}

/**
 * Renders [model] through the registry. An unregistered game or a foreign model is a
 * wiring bug — surfaced loudly, never silently blank.
 */
@Composable
fun GameUiRegistry.Board(
    gameId: GameId,
    model: RendererModel?,
    modifier: Modifier = Modifier,
) {
    val renderer = this[gameId]
    when {
        renderer == null -> error("no board renderer registered for $gameId")
        model == null -> BoardLoading(modifier = modifier)
        else -> renderer.Content(model, modifier)
    }
}
