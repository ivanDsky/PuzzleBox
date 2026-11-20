package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zipui.editor.ZipEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.flags.GameUiFlag
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class ZipEditorModeTilesWiringTest {

    // One Koin case, as in ZipCutWrongPartWiringTest: the flag set is flipped instead of a second container.
    @Test
    fun `the Koin module reads ModeTiles as each editor opens, and it is off by default`() {
        val on = MutableStateFlow(emptySet<ReleaseFlagKey>())
        val flags = object : ReleaseFlags {
            override val enabled = on
        }
        val koin = koinApplication { modules(zipUiModule, module { single<ReleaseFlags> { flags } }) }.koin
        val ui = koin.get<GameEditorUi>(qualifier("${ZipDefinition.id.value}.editor"))
        val draft = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())

        assertFalse(GameUiFlag.ModeTiles.releaseDefault)
        assertFalse((ui.createController(draft) {} as ZipEditorController).modeTiles)

        on.value = setOf(GameUiFlag.ModeTiles)
        assertTrue((ui.createController(draft) {} as ZipEditorController).modeTiles)
    }
}
