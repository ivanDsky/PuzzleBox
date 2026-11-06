package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.games.sudoku.sudokuModule
import com.ivandsky.puzzlebox.games.sudokuui.sudokuUiModule
import com.ivandsky.puzzlebox.model.GameId
import org.koin.core.module.Module

/** Games that ship in debug builds only until they have a live feed (ADR-0015). */
val buildTypeGameModules: List<Module> = listOf(sudokuModule, sudokuUiModule)

/** The 9x9 opens from debug settings only; no hub, Home, History or stats surface lists it. */
val buildTypeUnlistedGames: Set<GameId> = setOf(GameId("sudoku"))
