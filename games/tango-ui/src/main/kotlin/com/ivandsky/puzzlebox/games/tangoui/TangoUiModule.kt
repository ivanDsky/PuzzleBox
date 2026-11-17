package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.game.replay.ReplayFirstMove
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tangoui.editor.TangoEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules.
 *  Qualified by game id (the plugin binding's pattern) so the three play UIs coexist;
 *  the editor UI takes the `<id>.editor` qualifier beside it. The hint mode is built per board,
 *  the one place Tango reads [ReleaseFlag.HintFirstMove] (nav-rethink A2). */
val tangoUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(TangoDefinition.id.value)) {
        val compute = get<DispatcherProvider>().default
        val flags = get<ReleaseFlags>()
        TangoPlayUi { tangoHintMode(flags, compute) }
    }
    single<GameEditorUi>(qualifier = qualifier("${TangoDefinition.id.value}.editor")) { TangoEditorUi }
    // The replay of a recorded hint asks the stepper with the option the play UI gets; `tangoModule` picks this up.
    single<ReplayFirstMove>(qualifier = qualifier(TangoDefinition.id.value)) {
        val flags = getOrNull<ReleaseFlags>()
        ReplayFirstMove { tangoFirstMove(flags) }
    }
}

/** The Stepwise hint mode Tango plays with, computed on [compute]. */
internal fun tangoHintMode(flags: ReleaseFlags, compute: CoroutineDispatcher): HintMode.Stepwise =
    HintMode.Stepwise(compute, firstMove = tangoFirstMove(flags))

/** The option [tangoHintMode] puts in the mode, also what a replay of a recorded hint is asked with (ticket 47b). */
internal fun tangoFirstMove(flags: ReleaseFlags?): Boolean = flags?.isEnabled(ReleaseFlag.HintFirstMove) == true
