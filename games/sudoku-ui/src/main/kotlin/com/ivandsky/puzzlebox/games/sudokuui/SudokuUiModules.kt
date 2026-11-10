package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudokuui.editor.SudokuEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Mini Sudoku's play UI, qualified by its game id like the plugin binding; its editor UI takes the `<id>.editor` qualifier beside it. */
val miniSudokuUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(SudokuGames.MINI.id.value)) {
        SudokuPlayUi(
            SudokuGames.MINI,
            get<SudokuHighlightSettings>(),
            MiniSudokuBrand,
            HintModeSource { HintMode.Stepwise(get<DispatcherProvider>().default) },
        )
    }
    single<GameEditorUi>(qualifier = qualifier("${SudokuGames.MINI.id.value}.editor")) { SudokuEditorUi(SudokuGames.MINI) }
}

/** Classic Sudoku's play and editor UIs; only the debug build installs them (ADR-0015). */
val sudokuUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(SudokuGames.CLASSIC.id.value)) {
        SudokuPlayUi(SudokuGames.CLASSIC, get<SudokuHighlightSettings>(), ClassicSudokuBrand, HintModeSource { HintMode.Legacy })
    }
    single<GameEditorUi>(qualifier = qualifier("${SudokuGames.CLASSIC.id.value}.editor")) { SudokuEditorUi(SudokuGames.CLASSIC) }
}
