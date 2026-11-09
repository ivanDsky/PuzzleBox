package com.ivandsky.puzzlebox.games.tangoui.editor

import com.ivandsky.puzzlebox.game.VariantId
import org.junit.Assert.assertEquals
import org.junit.Test

class TangoEditorUiTest {

    @Test
    fun `the size label follows the variant`() {
        assertEquals("6×6", TangoEditorUi.sizeLabel(VariantId("tango/n6")))
        assertEquals("8×8", TangoEditorUi.sizeLabel(VariantId("tango/n8")))
    }

    @Test
    fun `an unknown variant labels itself`() {
        assertEquals("tango/n7", TangoEditorUi.sizeLabel(VariantId("tango/n7")))
    }
}
