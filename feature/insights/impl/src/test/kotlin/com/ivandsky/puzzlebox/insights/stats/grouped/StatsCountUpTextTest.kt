package com.ivandsky.puzzlebox.insights.stats.grouped

import org.junit.Assert.assertEquals
import org.junit.Test

/** A headline's text counts up: each number in it rolls from 0 to its value, the rest of the text stays. */
class StatsCountUpTextTest {

    @Test
    fun `a time rolls from 0 00 to its value and keeps its shape`() {
        assertEquals("0:00", countUpText("1:35", 0f))
        assertEquals("0:03", countUpText("1:35", 0.1f))
        assertEquals("0:17", countUpText("1:35", 0.5f))
        assertEquals("1:35", countUpText("1:35", 1f))
    }

    @Test
    fun `numbers inside words roll and the words stay`() {
        assertEquals("0 days", countUpText("14 days", 0f))
        assertEquals("7 days", countUpText("14 days", 0.5f))
        assertEquals("73%", countUpText("73%", 1f))
        assertEquals("36%", countUpText("73%", 0.5f))
    }

    @Test
    fun `thousands separators and leading zeros are kept`() {
        assertEquals("642 solves", countUpText("1,284 solves", 0.5f))
        assertEquals("1,284 solves", countUpText("1,284 solves", 1f))
        assertEquals("0:00:00", countUpText("1:30:10", 0f))
        assertEquals("0:05", countUpText("0:10", 0.5f))
    }

    @Test
    fun `text with no number is untouched`() {
        assertEquals("No replay", countUpText("No replay", 0.3f))
    }

    @Test
    fun `decimals keep their places`() {
        assertEquals("0.0 s", countUpText("12.4 s", 0f))
        assertEquals("6.2 s", countUpText("12.4 s", 0.5f))
        assertEquals("0:06.1", countUpText("0:12.3", 0.5f))
    }
}
