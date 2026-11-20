package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudokuui.editor.SudokuEditorUi
import com.ivandsky.puzzlebox.games.sudokuui.trainer.SudokuTrainer
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.flags.GameUiFlag
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/**
 * Mini Sudoku's play UI, qualified by its game id like the plugin binding; its editor UI takes the `<id>.editor` qualifier beside it,
 * and the Trainer binding `<id>.trainer`.
 *
 * The Trainer binding is a factory that reads [GameUiFlag.TrainSudoku] when asked and holds the Sudoku trainer only
 * while it is on, so with the flag off the registry gets nothing from Mini Sudoku: a Koin definition cannot be left out
 * by a flag, a holder that may be empty can. Classic Sudoku has no Trainer binding.
 */
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
    factory(qualifier = qualifier("${SudokuGames.MINI.id.value}.trainer")) {
        val on = get<ReleaseFlags>().isEnabled(GameUiFlag.TrainSudoku)
        TrainerGame.Binding(if (on) SudokuTrainer() else null)
    }
}

/** Classic Sudoku's play and editor UIs; only the debug build installs them (ADR-0015). */
val sudokuUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(SudokuGames.CLASSIC.id.value)) {
        SudokuPlayUi(SudokuGames.CLASSIC, get<SudokuHighlightSettings>(), ClassicSudokuBrand, HintModeSource { HintMode.Legacy })
    }
    single<GameEditorUi>(qualifier = qualifier("${SudokuGames.CLASSIC.id.value}.editor")) { SudokuEditorUi(SudokuGames.CLASSIC) }
}
