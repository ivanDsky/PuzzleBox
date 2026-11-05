package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.games.sudoku.sudokuModule
import com.ivandsky.puzzlebox.games.sudokuui.sudokuUiModule
import org.koin.core.module.Module

/** Games that ship in debug builds only until they have a live feed (ADR-0015). */
val buildTypeGameModules: List<Module> = listOf(sudokuModule, sudokuUiModule)
