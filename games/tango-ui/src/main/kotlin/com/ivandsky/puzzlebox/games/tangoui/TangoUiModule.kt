package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tangoui.editor.TangoEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules.
 *  Qualified by game id (the plugin binding's pattern) so the three play UIs coexist;
 *  the editor UI takes the `<id>.editor` qualifier beside it. The hint mode is Stepwise on the
 *  default dispatcher; [ReleaseFlag.HintPolish] is read per board. */
val tangoUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(TangoDefinition.id.value)) {
        val compute = get<DispatcherProvider>().default
        val flags = get<ReleaseFlags>()
        TangoPlayUi(
            HintMode.Stepwise(compute),
            plainCaptions = { tangoPlainCaptions(flags) },
        )
    }
    single<GameEditorUi>(qualifier = qualifier("${TangoDefinition.id.value}.editor")) { TangoEditorUi }
}

/** Whether Tango's hint callouts use the plain sentence set (HintPolish); the one place Tango reads that flag. */
internal fun tangoPlainCaptions(flags: ReleaseFlags?): Boolean = flags?.isEnabled(ReleaseFlag.HintPolish) == true
