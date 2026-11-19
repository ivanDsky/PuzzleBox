package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorUi
import com.ivandsky.puzzlebox.games.queensui.trainer.QueensTrainer
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.flags.GameUiFlag
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules.
 *  Qualified by game id (the plugin binding's pattern) so the three play UIs coexist; the
 *  editor UI takes the `<id>.editor` qualifier beside it, and the Trainer binding `<id>.trainer`.
 *
 *  The Trainer binding is a factory that reads [GameUiFlag.TrainQueens] when asked and holds the Queens trainer only
 *  while it is on, so with the flag off the registry gets nothing from Queens: a Koin definition cannot be left out
 *  by a flag, a holder that may be empty can. */
val queensUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(QueensDefinition.id.value)) {
        val compute = get<DispatcherProvider>().default
        QueensPlayUi(get<QueensSettings>(), { HintMode.Stepwise(compute) }, offersPlaceIt = true)
    }
    single<GameEditorUi>(qualifier = qualifier("${QueensDefinition.id.value}.editor")) { QueensEditorUi }
    factory(qualifier = qualifier("${QueensDefinition.id.value}.trainer")) {
        val on = get<ReleaseFlags>().isEnabled(GameUiFlag.TrainQueens)
        TrainerGame.Binding(if (on) QueensTrainer(get<DispatcherProvider>().default) else null)
    }
}
