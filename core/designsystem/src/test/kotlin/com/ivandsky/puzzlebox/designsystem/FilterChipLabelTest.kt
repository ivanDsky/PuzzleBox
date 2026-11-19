package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

// Bug it catches: a chip that keeps saying "Source" after a pick, or shows a count for a single pick.
class FilterChipLabelTest {

    @Test
    fun `nothing picked says the group's name`() {
        assertEquals("Source", filterChipLabel("Source", emptyList()))
    }

    @Test
    fun `one pick says that row's label`() {
        assertEquals("From friends", filterChipLabel("Source", listOf("From friends")))
    }

    @Test
    fun `several picks say the name and the count`() {
        assertEquals("Source · 2", filterChipLabel("Source", listOf("Community", "Mine")))
        assertEquals("Source · 3", filterChipLabel("Source", listOf("Community", "Mine", "Past days")))
    }
}
