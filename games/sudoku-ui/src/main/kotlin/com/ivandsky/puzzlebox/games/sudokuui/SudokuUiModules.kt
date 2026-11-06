package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Mini Sudoku's play UI, qualified by its game id like the plugin binding. */
val miniSudokuUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(SudokuGames.MINI.id.value)) { SudokuPlayUi(SudokuGames.MINI, MiniSudokuBrand) }
}

/** Classic Sudoku's play UI; installed once its daily source ships (ticket 08). */
val sudokuUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(SudokuGames.CLASSIC.id.value)) { SudokuPlayUi(SudokuGames.CLASSIC) }
}
