package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.games.zipui.ZipBoardRenderer
import com.ivandsky.puzzlebox.model.GameId
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * :app's aggregation point (ADR-0001): the composition root owns the seams no game
 * module may know about. Adding a game means exactly two lines below — its module in
 * [PuzzleBoxApplication.appModules] and one registry registration here.
 */
val appModule: Module = module {

    // Generic plugin list for consumers that must not know game ids (Home, play shell).
    // Registered as one binding so feature modules inject a plain List instead of
    // reaching for Koin's getAll themselves.
    single<List<GamePlugin<*, *, *>>> { getAll<GamePlugin<*, *, *>>() }

    // One registration line per game — the whole extension point (F2-1).
    single {
        GameUiRegistry().apply {
            register(GameId("zip"), get<ZipBoardRenderer>())
        }
    }
}
