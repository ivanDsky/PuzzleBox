package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.model.GameId
import org.koin.core.module.Module

/** Release ships only games with a live feed; debug-only games stay out (ADR-0015). */
val buildTypeGameModules: List<Module> = emptyList()

/** Release ships no unlisted game. */
val buildTypeUnlistedGames: Set<GameId> = emptySet()
