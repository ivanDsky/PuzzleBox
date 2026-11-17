package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.game.replay.ReplayFirstMove
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules.
 *  Qualified by game id (the plugin binding's pattern) so the three play UIs coexist; the
 *  editor UI takes the `<id>.editor` qualifier beside it. */
val queensUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(QueensDefinition.id.value)) {
        val flags = getOrNull<ReleaseFlags>()
        val compute = get<DispatcherProvider>().default
        QueensPlayUi(get<QueensSettings>()) { queensHintMode(flags, compute) }
    }
    single<GameEditorUi>(qualifier = qualifier("${QueensDefinition.id.value}.editor")) { QueensEditorUi }
    // The replay of a recorded hint asks the stepper with the option the play UI gets; `queensModule` picks this up.
    single<ReplayFirstMove>(qualifier = qualifier(QueensDefinition.id.value)) {
        val flags = getOrNull<ReleaseFlags>()
        ReplayFirstMove { queensFirstMove(flags) }
    }
}

/**
 * The Stepwise hint mode Queens plays with, computed on [compute]. The one place Queens reads `HintFirstMove` (A2);
 * a graph with no [flags] bound keeps the ladder order.
 */
internal fun queensHintMode(flags: ReleaseFlags?, compute: CoroutineDispatcher): HintMode.Stepwise =
    HintMode.Stepwise(compute, firstMove = queensFirstMove(flags))

/** The option [queensHintMode] puts in the mode, also what a replay of a recorded hint is asked with (ticket 47b). */
internal fun queensFirstMove(flags: ReleaseFlags?): Boolean = flags?.isEnabled(ReleaseFlag.HintFirstMove) == true
