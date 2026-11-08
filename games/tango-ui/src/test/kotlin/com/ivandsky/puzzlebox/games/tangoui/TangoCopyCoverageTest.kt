package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.editor.TangoDraftAnalyzer
import com.ivandsky.puzzlebox.games.tango.editor.TangoPublishGates
import com.ivandsky.puzzlebox.games.tangoui.editor.TangoEditorCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every id the Tango engine can hand the UI has fix-list copy. */
class TangoCopyCoverageTest {

    @Test
    fun `every reason and block id has copy`() {
        val ids = TangoDraftAnalyzer.reasonIds + TangoPublishGates.blockIds
        assertTrue("the engine lists its ids", ids.isNotEmpty())

        val missing = ids.filter { TangoEditorCopy.fix(it).isNullOrBlank() }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `an unknown id has no copy`() {
        assertNull(TangoEditorCopy.fix("tango.unknown"))
    }
}
