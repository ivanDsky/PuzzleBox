package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tangoui.editor.TangoEditorUi
import com.ivandsky.puzzlebox.games.tangoui.trainer.TangoTrainer
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules.
 *  Qualified by game id (the plugin binding's pattern) so the three play UIs coexist;
 *  the editor UI takes the `<id>.editor` qualifier beside it, and the Trainer binding `<id>.trainer`.
 *  The hint mode is Stepwise on the default dispatcher.
 */
val tangoUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(TangoDefinition.id.value)) {
        val compute = get<DispatcherProvider>().default
        TangoPlayUi(HintMode.Stepwise(compute))
    }
    single<GameEditorUi>(qualifier = qualifier("${TangoDefinition.id.value}.editor")) { TangoEditorUi }
    factory(qualifier = qualifier("${TangoDefinition.id.value}.trainer")) {
        TrainerGame.Binding(TangoTrainer(get<DispatcherProvider>().default))
    }
}
