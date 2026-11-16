package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strandsui.editor.StrandsEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules,
 *  qualified by game id so every play UI coexists; the editor UI takes the `<id>.editor`
 *  qualifier beside it. The one Strands read of `HintFirstMove` is here (nav-rethink A2):
 *  each new controller gets the first-move hint when the flag is on, today's hint otherwise. */
val strandsUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(StrandsDefinition.id.value)) {
        val flags = get<ReleaseFlags>()
        val compute = get<DispatcherProvider>().default
        StrandsPlayUi(
            HintModeSource {
                if (flags.isEnabled(ReleaseFlag.HintFirstMove)) HintMode.FirstMove(compute) else HintMode.Legacy
            },
        )
    }
    single<GameEditorUi>(qualifier = qualifier("${StrandsDefinition.id.value}.editor")) { StrandsEditorUi }
}
