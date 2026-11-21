package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zipui.di.zipUiFlagModule
import com.ivandsky.puzzlebox.games.zipui.editor.ZipEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules.
 *  Qualified by game id (the plugin binding's pattern) so the three play UIs coexist; the
 *  editor UI takes the `<id>.editor` qualifier beside it. The editor asks the
 *  [ZipUiFlag.NumbersAndWalls] flag each time it opens, and the play UI asks [ZipUiFlag.CutWrongPart]
 *  each time a puzzle opens, so a debug switch shows on the next open. */
val zipUiModule: Module = module {
    includes(zipUiFlagModule)
    single<GamePlayUi>(qualifier = qualifier(ZipDefinition.id.value)) {
        val flags = get<ReleaseFlags>()
        ZipPlayUi(cutWrongPart = { flags.isEnabled(ZipUiFlag.CutWrongPart) })
    }
    single<GameEditorUi>(qualifier = qualifier("${ZipDefinition.id.value}.editor")) {
        val flags = get<ReleaseFlags>()
        ZipEditorUi(combined = { flags.isEnabled(ZipUiFlag.NumbersAndWalls) })
    }
}
