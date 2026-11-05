package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.games.sudoku.feed.BundledSudokuDailySource
import com.ivandsky.puzzlebox.games.sudoku.feed.LinkedInMiniSudokuDailySource
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.bind
import org.koin.dsl.module

/** Mini Sudoku's registration (ADR-0001): the plugin and the LinkedIn daily source. */
val miniSudokuModule: Module = module {
    single(qualifier = qualifier(SudokuGames.MINI.id.value)) { sudokuPlugin(SudokuGames.MINI) }
    single(qualifier = qualifier(SudokuGames.MINI.id.value)) { LinkedInMiniSudokuDailySource(get()) } bind DailySource::class
}

/** Classic 9x9 Sudoku's registration: the plugin and the bundled daily (debug builds only, ADR-0015). */
val sudokuModule: Module = module {
    single(qualifier = qualifier(SudokuGames.CLASSIC.id.value)) { sudokuPlugin(SudokuGames.CLASSIC) }
    single(qualifier = qualifier(SudokuGames.CLASSIC.id.value)) { BundledSudokuDailySource() } bind DailySource::class
}
