package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.editor.ZipEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules.
 *  Qualified by game id (the plugin binding's pattern) so the three play UIs coexist; the
 *  editor UI takes the `<id>.editor` qualifier beside it. */
val zipUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(ZipDefinition.id.value)) {
        ZipPlayUi(zipHintMode(getOrNull<ReleaseFlags>()) { get<DispatcherProvider>() })
    }
    single<GameEditorUi>(qualifier = qualifier("${ZipDefinition.id.value}.editor")) { ZipEditorUi }
}

/**
 * The one place Zip reads the HintFirstMove release flag (nav-rethink A2), once per graph: the next
 * cell with the flag off or with no flags bound, the first real choice with it on. Zip's hint walks
 * the stored solution on the caller's thread, so the dispatcher is carried and not used yet.
 */
internal fun zipHintMode(flags: ReleaseFlags?, dispatchers: () -> DispatcherProvider): HintMode =
    if (flags?.isEnabled(ReleaseFlag.HintFirstMove) == true) HintMode.FirstMove(dispatchers().default) else HintMode.Legacy
