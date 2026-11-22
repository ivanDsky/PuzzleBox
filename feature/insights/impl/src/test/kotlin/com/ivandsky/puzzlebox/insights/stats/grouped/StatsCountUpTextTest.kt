package com.ivandsky.puzzlebox.insights.stats.grouped

import org.junit.Assert.assertEquals
import org.junit.Test

/** A headline's text counts up: each number in it rolls from 0 to its value, the rest of the text stays. */
class StatsCountUpTextTest {

    @Test
    fun `a time counts its total seconds, so minutes and seconds roll as one number`() {
        assertEquals("0:00", countUpText("1:35", 0f))
        assertEquals("0:09", countUpText("1:35", 0.1f))
        assertEquals("0:47", countUpText("1:35", 0.5f))
        assertEquals("1:11", countUpText("1:35", 0.75f))
        assertEquals("1:35", countUpText("1:35", 1f))
    }

    @Test
    fun `an hours time counts as one number too and keeps its shape`() {
        assertEquals("0:00:00", countUpText("1:30:10", 0f))
        assertEquals("0:45:05", countUpText("1:30:10", 0.5f))
        assertEquals("1:30:10", countUpText("1:30:10", 1f))
        assertEquals("0:05", countUpText("0:10", 0.5f))
    }

    @Test
    fun `a time inside words counts and the words stay`() {
        assertEquals("Best 0:47 today", countUpText("Best 1:35 today", 0.5f))
    }

    @Test
    fun `a change counts from the old value to the new one`() {
        assertEquals("312", countUpText("1,284", 0f, from = "312"))
        assertEquals("798", countUpText("1,284", 0.5f, from = "312"))
        assertEquals("1,284", countUpText("1,284", 1f, from = "312"))
        assertEquals("1:35", countUpText("0:35", 0f, from = "1:35"))
        assertEquals("1:05", countUpText("0:35", 0.5f, from = "1:35"))
        assertEquals("0:35", countUpText("0:35", 1f, from = "1:35"))
    }

    @Test
    fun `counting down works and a number with no old partner starts from zero`() {
        assertEquals("60%", countUpText("40%", 0.5f, from = "80%"))
        assertEquals("5 of 2", countUpText("10 of 4", 0.5f, from = "0"))
        assertEquals("No data", countUpText("No data", 0.5f, from = "312"))
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
