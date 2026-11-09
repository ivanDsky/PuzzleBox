package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.VariantId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TangoDraftTest {

    @Test
    fun `the size table is 4, 6, 8 and 10`() {
        assertEquals(listOf(4, 6, 8, 10), TangoDraft.SIZES)
    }

    @Test
    fun `a width outside the size table is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TangoDraft(5, emptyMap(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { TangoDraft(12, emptyMap(), emptyList()) }
    }

    @Test
    fun `every size round-trips through its variant`() {
        TangoDraft.SIZES.forEach { n ->
            assertEquals(n, TangoDraft.sizeOf(TangoDraft.variantOf(n)))
            assertEquals(n, TangoDraft(n, emptyMap(), emptyList()).n)
        }
    }

    @Test
    fun `a variant outside the size table has no size`() {
        assertNull(TangoDraft.sizeOf(VariantId("tango/n7")))
    }
}
