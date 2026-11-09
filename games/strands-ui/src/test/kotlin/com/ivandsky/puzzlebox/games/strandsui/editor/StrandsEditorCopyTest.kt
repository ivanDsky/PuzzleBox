package com.ivandsky.puzzlebox.games.strandsui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/** The word field's overflow line, as the copy function returns it: counts read right at 1 and above. */
class StrandsEditorCopyTest {

    @Test
    fun `one extra letter reads in the singular`() {
        assertEquals("5 letters, path has 4: the extra 1 isn't written", StrandsEditorCopy.overflow(5, 4))
    }

    @Test
    fun `several extra letters read in the plural`() {
        assertEquals("6 letters, path has 4: the extra 2 aren't written", StrandsEditorCopy.overflow(6, 4))
        assertEquals("24 letters, path has 4: the extra 20 aren't written", StrandsEditorCopy.overflow(24, 4))
    }
}
