package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strandsui.editor.StrandsEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module

/** Koin wiring (ADR-0001 aggregation): `app` collects this with the other ui modules,
 *  qualified by game id so every play UI coexists; the editor UI takes the `<id>.editor`
 *  qualifier beside it. */
val strandsUiModule: Module = module {
    single<GamePlayUi>(qualifier = qualifier(StrandsDefinition.id.value)) { StrandsPlayUi() }
    single<GameEditorUi>(qualifier = qualifier("${StrandsDefinition.id.value}.editor")) { StrandsEditorUi }
}
