package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Variant table: LinkedIn's daily sizes as data (ADR-0005); unknown ids are never guessed. */
class ZipVariantsTest {

    @Test
    fun `table carries the daily sizes`() {
        assertEquals(setOf("zip/n5", "zip/n6", "zip/n7", "zip/n8"), ZipVariants.table.keys.map { it.value }.toSet())
        ZipVariants.table.values.forEach { variant ->
            assertEquals(variant.id.value.removePrefix("zip/n").toInt(), variant.size)
            assertTrue(variant.features.isEmpty()) // anti-speculation: empty until a consumer exists
        }
    }

    /** Spec A11/A18: the lessons' tiny boards live apart, so dailies keep exactly 5..8. */
    @Test
    fun `the daily list is exactly 5 to 8 and leaves the tutorial sizes out`() {
        assertEquals(listOf(5, 6, 7, 8), ZipVariants.all.map { it.size }.sorted())
        assertEquals(listOf(3, 4), ZipVariants.tutorial.values.map { it.size }.sorted())
        ZipVariants.tutorial.values.forEach { variant ->
            assertEquals("zip/n${variant.size}", variant.id.value)
            assertTrue(variant.features.isEmpty())
        }
    }

    @Test
    fun `byId resolves daily and tutorial sizes, dailyById only the daily ones`() {
        listOf(3, 4, 5, 8).forEach { n ->
            assertEquals(n, ZipVariants.byId(VariantId("zip/n$n"))?.size)
        }
        assertNull(ZipVariants.dailyById(VariantId("zip/n3")))
        assertNull(ZipVariants.dailyById(VariantId("zip/n4")))
        assertEquals(5, ZipVariants.dailyById(VariantId("zip/n5"))?.size)
    }

    @Test
    fun `byId returns null for unknown regimes`() {
        assertNull(ZipVariants.byId(VariantId("zip/n2")))
        assertNull(ZipVariants.byId(VariantId("zip/n9")))
        assertNull(ZipVariants.byId(VariantId("queens/6")))
        assertNull(ZipVariants.dailyById(VariantId("zip/n9")))
    }

    @Test
    fun `all mirrors table`() {
        assertEquals(ZipVariants.table.values.toSet(), ZipVariants.all.toSet())
    }
}
