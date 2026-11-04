package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.games.queensui.registerQueensBoard
import com.ivandsky.puzzlebox.play.PlayFlow
import com.ivandsky.puzzlebox.play.registerZipBoard
import org.koin.dsl.module

/**
 * Play feature wiring. The registry is the game-ui composition point: the Compose
 * mapping for a game's renderer model lives where both sides are visible (F2 finding
 * F2-1) — here, for Zip and Queens (queens-10). The
 * [com.ivandsky.puzzlebox.data.repository.CompletionRepository] comes from :core:data's
 * dataModule (pinned ticket-15 contract); this module adds no data bindings.
 *
 * Ticket 23: [PlayFlow] is a single so the play state outlives the play entry's
 * composition across the result route — the record write never gets cancelled by
 * navigation and "Play again" resets in place. One flow drives every game: the picker
 * (A14) starts Zip, Tango or Queens off their daily repositories.
 */
val playModule = module {
    single {
        GameUiRegistry().apply {
            registerZipBoard()
            registerQueensBoard()
        }
    }
    single { PlayFlow(daily = get(), completions = get()) }
}
