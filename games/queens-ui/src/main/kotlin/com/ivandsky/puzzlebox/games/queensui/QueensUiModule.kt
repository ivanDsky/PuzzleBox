package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel
import org.koin.core.module.Module
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules. */
val queensUiModule: Module = module {
    // Concrete binding (zip ZipUiModule precedent) so the app-level registry line can
    // resolve the renderer when it registers the Queens board (queens-10).
    factory { QueensBoardRenderer() }
}

/**
 * The F2-1 Compose mapping's registry seam (zip's registerZipBoard precedent): the play
 * feature registers the Queens board where both sides are visible. Skeleton wiring — the
 * play-flow integration ticket calls this alongside registerZipBoard.
 */
fun GameUiRegistry.registerQueensBoard() {
    register(QueensDefinition.id) { model, modifier ->
        QueensBoard(model as QueensRendererModel, modifier)
    }
}

/** Read-only board (no gesture layer): the registry-facing rendering of the model. */
@Composable
fun QueensBoard(model: QueensRendererModel, modifier: Modifier = Modifier) {
    QueensBoardRenderer().Content(model, modifier)
}
