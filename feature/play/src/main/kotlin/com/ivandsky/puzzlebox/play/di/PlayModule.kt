package com.ivandsky.puzzlebox.play.di

import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.play.registerZipBoard
import org.koin.dsl.module

/**
 * Play feature wiring. The registry is the game-ui composition point: the Compose
 * mapping for a game's renderer model lives where both sides are visible (F2 finding
 * F2-1) — here, for Zip. The [com.ivandsky.puzzlebox.data.repository.CompletionRepository]
 * comes from :core:data's dataModule (pinned ticket-15 contract); this module adds no
 * data bindings.
 */
val playModule = module {
    single {
        GameUiRegistry().apply { registerZipBoard() }
    }
}
